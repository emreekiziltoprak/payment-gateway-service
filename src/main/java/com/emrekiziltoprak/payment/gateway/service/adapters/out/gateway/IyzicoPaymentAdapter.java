package com.emrekiziltoprak.payment.gateway.service.adapters.out.gateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.ProviderPaymentReference;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.GatewayTimeoutException;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.PaymentGatewayException;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CaptureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.FailureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.LifecycleObservationContext;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ProcessingObservation;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;

@Component("iyzicoPaymentAdapter")
public class IyzicoPaymentAdapter implements PaymentGatewayPort {

    private final RestTemplate restTemplate;
    private final Clock clock;
    private final JsonMapper jsonMapper;

    @Value("${iyzico.api.url:https://api.iyzipay.com/payment/auth}")
    private String iyzicoApiUrl;

    @Autowired
    public IyzicoPaymentAdapter(RestTemplate restTemplate, JsonMapper jsonMapper) {
        this(restTemplate, Clock.systemUTC(), jsonMapper);
    }

    IyzicoPaymentAdapter(RestTemplate restTemplate, Clock clock) {
        this(restTemplate, clock, JsonMapper.builder().build());
    }

    IyzicoPaymentAdapter(RestTemplate restTemplate, Clock clock, JsonMapper jsonMapper) {
        this.restTemplate = restTemplate;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public PaymentLifecycleObservation processPayment(Payment payment) {
        try {
            IyzicoPaymentResponse response = restTemplate.postForObject(
                    iyzicoApiUrl,
                    mapToIyzicoRequest(payment),
                    IyzicoPaymentResponse.class
            );
            return mapToObservation(payment, response);
        } catch (ResourceAccessException | HttpServerErrorException.GatewayTimeout exception) {
            throw new GatewayTimeoutException("Iyzico gateway timeout", exception);
        } catch (HttpStatusCodeException exception) {
            return mapHttpError(payment, exception);
        } catch (RestClientException exception) {
            throw new PaymentGatewayException(
                    "Iyzico response could not be processed",
                    exception
            );
        }
    }

    private PaymentLifecycleObservation mapHttpError(
            Payment payment,
            HttpStatusCodeException exception
    ) {
        IyzicoPaymentResponse response = deserializeError(exception);
        if (response != null && response.status != null && !response.status.isBlank()) {
            return mapToObservation(payment, response);
        }

        if (exception.getStatusCode().is5xxServerError()) {
            throw new GatewayTimeoutException(
                    "Iyzico gateway returned an indeterminate server error",
                    exception
            );
        }

        throw new PaymentGatewayException(
                "Iyzico rejected the request without lifecycle failure facts",
                exception
        );
    }

    private IyzicoPaymentResponse deserializeError(HttpStatusCodeException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            return jsonMapper.readValue(responseBody, IyzicoPaymentResponse.class);
        } catch (JacksonException exceptionDuringMapping) {
            return null;
        }
    }

    private IyzicoPaymentRequest mapToIyzicoRequest(Payment payment) {
        IyzicoPaymentRequest request = new IyzicoPaymentRequest();
        request.locale = "tr";
        request.conversationId = payment.getId().value().toString();
        request.price = payment.getAmount().amount();
        request.paidPrice = payment.getAmount().amount();
        request.currency = payment.getAmount().currency().getCurrencyCode();
        request.installment = 1;
        request.paymentChannel = "WEB";
        request.basketId = payment.getId().value().toString();
        request.paymentGroup = "PRODUCT";
        return request;
    }

    private PaymentLifecycleObservation mapToObservation(
            Payment payment,
            IyzicoPaymentResponse response
    ) {
        if (response == null) {
            throw new PaymentGatewayException("Iyzico returned no payment result");
        }
        if (response.status == null || response.status.isBlank()) {
            throw new PaymentGatewayException("Iyzico response is missing status");
        }
        validateConversationId(payment, response.conversationId);

        if ("failure".equalsIgnoreCase(response.status)) {
            return new FailureObservation(
                    failureContext(payment, response),
                    providerFailure(response)
            );
        }
        if (!"success".equalsIgnoreCase(response.status)) {
            throw new PaymentGatewayException(
                    "Unsupported Iyzico status: " + response.status
            );
        }
        if (response.fraudStatus == null) {
            throw new PaymentGatewayException("Iyzico success response is missing fraudStatus");
        }

        return switch (response.fraudStatus) {
            case 1 -> new CaptureObservation(successContext(payment, response));
            case 0 -> new ProcessingObservation(successContext(payment, response));
            case -1 -> new FailureObservation(
                    successContext(payment, response),
                    PaymentFailure.fromProvider(
                            PaymentFailureCode.DECLINED,
                            response.errorCode != null ? response.errorCode : "fraud_status_-1",
                            response.errorGroup,
                            response.errorMessage != null
                                    ? response.errorMessage
                                    : "Iyzico rejected the payment after fraud evaluation"
                    )
            );
            default -> throw new PaymentGatewayException(
                    "Unsupported Iyzico fraudStatus: " + response.fraudStatus
            );
        };
    }

    private LifecycleObservationContext successContext(
            Payment payment,
            IyzicoPaymentResponse response
    ) {
        if (response.paymentId == null || response.paymentId.isBlank()) {
            throw new PaymentGatewayException("Iyzico success response is missing paymentId");
        }
        if (response.price == null) {
            throw new PaymentGatewayException("Iyzico success response is missing price");
        }
        if (response.currency == null || response.currency.isBlank()) {
            throw new PaymentGatewayException("Iyzico success response is missing currency");
        }

        Money amount;
        try {
            amount = Money.of(response.price, response.currency);
        } catch (RuntimeException exception) {
            throw new PaymentGatewayException("Iyzico response contains invalid price or currency");
        }

        return context(payment, response, amount);
    }

    private LifecycleObservationContext failureContext(
            Payment payment,
            IyzicoPaymentResponse response
    ) {
        Money amount = payment.getAmount();
        boolean hasPrice = response.price != null;
        boolean hasCurrency = response.currency != null && !response.currency.isBlank();
        if (hasPrice != hasCurrency) {
            throw new PaymentGatewayException(
                    "Iyzico failure response contains incomplete amount facts"
            );
        }
        if (hasPrice) {
            try {
                amount = Money.of(response.price, response.currency);
            } catch (RuntimeException exception) {
                throw new PaymentGatewayException("Iyzico response contains invalid price or currency");
            }
        }
        return context(payment, response, amount);
    }

    private LifecycleObservationContext context(
            Payment payment,
            IyzicoPaymentResponse response,
            Money amount
    ) {
        return new LifecycleObservationContext(
                Optional.of(payment.getId()),
                new ProviderPaymentReference(
                        PaymentProvider.IYZICO,
                        blankToNull(response.paymentId)
                ),
                amount,
                clock.instant(),
                Optional.ofNullable(response.systemTime).map(Instant::ofEpochMilli)
        );
    }

    private PaymentFailure providerFailure(IyzicoPaymentResponse response) {
        PaymentFailureCode code = classifyFailure(response.errorCode, response.errorGroup);
        String detail = response.errorMessage != null
                ? response.errorMessage
                : "Iyzico reported a technical payment failure";
        return PaymentFailure.fromProvider(
                code,
                response.errorCode,
                response.errorGroup,
                detail
        );
    }

    private PaymentFailureCode classifyFailure(String providerCode, String errorGroup) {
        String normalizedGroup = errorGroup == null
                ? ""
                : errorGroup.toUpperCase(Locale.ROOT);
        if ((providerCode != null && providerCode.startsWith("10"))
                || "6000".equals(providerCode)
                || "6001".equals(providerCode)
                || normalizedGroup.contains("DECLIN")
                || normalizedGroup.contains("NOT_SUFFICIENT")) {
            return PaymentFailureCode.DECLINED;
        }
        if (normalizedGroup.contains("TIMEOUT")) {
            return PaymentFailureCode.TIMEOUT;
        }
        if (normalizedGroup.contains("VALIDATION")
                || normalizedGroup.contains("INVALID")) {
            return PaymentFailureCode.VALIDATION_ERROR;
        }
        return PaymentFailureCode.GATEWAY_ERROR;
    }

    private void validateConversationId(Payment payment, String conversationId) {
        if (conversationId != null
                && !conversationId.isBlank()
                && !payment.getId().value().toString().equals(conversationId)) {
            throw new PaymentGatewayException("Iyzico conversationId does not match payment ID");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static class IyzicoPaymentRequest {
        public String locale;
        public String conversationId;
        public BigDecimal price;
        public BigDecimal paidPrice;
        public String currency;
        public Integer installment;
        public String paymentChannel;
        public String basketId;
        public String paymentGroup;
    }

    static class IyzicoPaymentResponse {
        public String status;
        public String locale;
        public Long systemTime;
        public String conversationId;
        public String paymentId;
        public Integer fraudStatus;
        public BigDecimal price;
        public BigDecimal paidPrice;
        public String currency;
        public String paymentStatus;
        public String basketId;
        public String errorCode;
        public String errorMessage;
        public String errorGroup;
        public String errorName;
        public String signature;
        public List<ItemTransaction> itemTransactions;
    }

    static class ItemTransaction {
        public String itemId;
        public String paymentTransactionId;
        public Integer transactionStatus;
    }
}
