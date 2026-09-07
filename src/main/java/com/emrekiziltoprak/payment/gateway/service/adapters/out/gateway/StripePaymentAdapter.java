package com.emrekiziltoprak.payment.gateway.service.adapters.out.gateway;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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

import com.emrekiziltoprak.payment.gateway.service.adapters.stripe.StripeLifecycleMapping;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.ProviderPaymentReference;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.GatewayTimeoutException;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.PaymentGatewayException;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ActionRequiredObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.AuthorizationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CancellationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CaptureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.FailureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.LifecycleObservationContext;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ProcessingObservation;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;

@Component("stripePaymentAdapter")
public class StripePaymentAdapter implements PaymentGatewayPort {

    private final RestTemplate restTemplate;
    private final Clock clock;
    private final JsonMapper jsonMapper;

    @Value("${stripe.api.url:https://api.stripe.com/v1/payment_intents}")
    private String stripeApiUrl;

    @Autowired
    public StripePaymentAdapter(RestTemplate restTemplate, JsonMapper jsonMapper) {
        this(restTemplate, Clock.systemUTC(), jsonMapper);
    }

    StripePaymentAdapter(RestTemplate restTemplate, Clock clock) {
        this(restTemplate, clock, JsonMapper.builder().build());
    }

    StripePaymentAdapter(RestTemplate restTemplate, Clock clock, JsonMapper jsonMapper) {
        this.restTemplate = restTemplate;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public PaymentLifecycleObservation processPayment(Payment payment) {
        try {
            StripePaymentResponse response = restTemplate.postForObject(
                    stripeApiUrl,
                    mapToStripeRequest(payment),
                    StripePaymentResponse.class
            );
            return mapToObservation(payment, response);
        } catch (ResourceAccessException | HttpServerErrorException.GatewayTimeout exception) {
            throw new GatewayTimeoutException("Stripe gateway timeout", exception);
        } catch (HttpStatusCodeException exception) {
            return mapHttpError(payment, exception);
        } catch (RestClientException exception) {
            throw new PaymentGatewayException(
                    "Stripe response could not be processed",
                    exception
            );
        }
    }

    private PaymentLifecycleObservation mapHttpError(
            Payment payment,
            HttpStatusCodeException exception
    ) {
        StripeErrorResponse response = deserializeError(exception);
        if (response != null && response.error != null) {
            StripePaymentError error = response.error;
            StripePaymentResponse paymentIntent = error.payment_intent;
            return new FailureObservation(
                    httpFailureContext(payment, paymentIntent),
                    StripeLifecycleMapping.failure(
                            error.type,
                            error.code,
                            error.decline_code,
                            error.message
                    )
            );
        }

        if (exception.getStatusCode().is5xxServerError()) {
            throw new GatewayTimeoutException(
                    "Stripe gateway returned an indeterminate server error",
                    exception
            );
        }

        throw new PaymentGatewayException(
                "Stripe rejected the request without lifecycle failure facts",
                exception
        );
    }

    private StripeErrorResponse deserializeError(HttpStatusCodeException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            return jsonMapper.readValue(responseBody, StripeErrorResponse.class);
        } catch (JacksonException exceptionDuringMapping) {
            return null;
        }
    }

    private LifecycleObservationContext httpFailureContext(
            Payment payment,
            StripePaymentResponse paymentIntent
    ) {
        ProviderPaymentReference providerReference = new ProviderPaymentReference(
                PaymentProvider.STRIPE,
                paymentIntent == null ? null : blankToNull(paymentIntent.id)
        );
        Money failureAmount = payment.getAmount();

        if (paymentIntent != null) {
            boolean hasAmount = paymentIntent.amount != null;
            boolean hasCurrency = paymentIntent.currency != null
                    && !paymentIntent.currency.isBlank();
            if (hasAmount != hasCurrency) {
                throw new PaymentGatewayException(
                        "Stripe error PaymentIntent contains incomplete amount facts"
                );
            }
            if (hasAmount) {
                failureAmount = StripeLifecycleMapping.moneyFromMinorUnits(
                        paymentIntent.amount,
                        paymentIntent.currency,
                        "amount"
                );
            }
        }

        return new LifecycleObservationContext(
                Optional.of(payment.getId()),
                providerReference,
                failureAmount,
                clock.instant(),
                Optional.empty()
        );
    }

    private StripePaymentRequest mapToStripeRequest(Payment payment) {
        StripePaymentRequest request = new StripePaymentRequest();
        request.amount = StripeLifecycleMapping.moneyToMinorUnits(payment.getAmount());
        request.currency = payment.getAmount().currency().getCurrencyCode()
                .toLowerCase(Locale.ROOT);

        request.metadata = new HashMap<>();
        request.metadata.put("payment_id", payment.getId().value().toString());
        request.metadata.put("source_account", payment.getSourceAccountId().value().toString());
        request.metadata.put(
                "destination_account",
                payment.getDestinationAccountId().value().toString()
        );
        return request;
    }

    private PaymentLifecycleObservation mapToObservation(
            Payment payment,
            StripePaymentResponse response
    ) {
        if (response == null) {
            throw new PaymentGatewayException("Stripe returned no payment result");
        }
        if (response.status == null || response.status.isBlank()) {
            throw new PaymentGatewayException("Stripe response is missing status");
        }

        return switch (response.status) {
            case "succeeded" -> new CaptureObservation(
                    context(payment, response, response.amount_received, "amount_received",
                            Optional.empty())
            );
            case "requires_capture" -> new AuthorizationObservation(
                    context(payment, response, response.amount_capturable, "amount_capturable",
                            Optional.empty())
            );
            case "requires_action" -> new ActionRequiredObservation(
                    context(payment, response, response.amount, "amount", Optional.empty()),
                    StripeLifecycleMapping.requiredAction(
                            response.next_action == null ? null : response.next_action.type,
                            redirectUrl(response.next_action)
                    )
            );
            case "processing" -> new ProcessingObservation(
                    context(payment, response, response.amount, "amount", Optional.empty())
            );
            case "requires_payment_method" -> new FailureObservation(
                    context(payment, response, response.amount, "amount", Optional.empty()),
                    failure(response.last_payment_error)
            );
            case "canceled" -> new CancellationObservation(
                    context(
                            payment,
                            response,
                            response.amount,
                            "amount",
                            Optional.ofNullable(response.canceled_at).map(Instant::ofEpochSecond)
                    ),
                    StripeLifecycleMapping.cancellation(response.cancellation_reason)
            );
            default -> throw new PaymentGatewayException(
                    "Unsupported Stripe status: " + response.status
            );
        };
    }

    private LifecycleObservationContext context(
            Payment payment,
            StripePaymentResponse response,
            Long amountInMinorUnits,
            String materialFact,
            Optional<Instant> providerOccurredAt
    ) {
        if (response.id == null || response.id.isBlank()) {
            throw new PaymentGatewayException("Stripe response is missing provider reference");
        }

        Money observedAmount = StripeLifecycleMapping.moneyFromMinorUnits(
                amountInMinorUnits,
                response.currency,
                materialFact
        );

        return new LifecycleObservationContext(
                Optional.of(payment.getId()),
                new ProviderPaymentReference(PaymentProvider.STRIPE, response.id),
                observedAmount,
                clock.instant(),
                providerOccurredAt
        );
    }

    private PaymentFailure failure(StripePaymentError error) {
        if (error == null) {
            throw new PaymentGatewayException(
                    "Stripe requires_payment_method response has no last_payment_error"
            );
        }
        return StripeLifecycleMapping.failure(
                error.type,
                error.code,
                error.decline_code,
                error.message
        );
    }

    private String redirectUrl(StripeNextAction action) {
        if (action == null || action.redirect_to_url == null) {
            return null;
        }
        return action.redirect_to_url.url;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static class StripePaymentRequest {
        public Long amount;
        public String currency;
        public Map<String, String> metadata;
    }

    static class StripePaymentResponse {
        public String id;
        public Long amount;
        public Long amount_received;
        public Long amount_capturable;
        public String currency;
        public String status;
        public String client_secret;
        public Long canceled_at;
        public String cancellation_reason;
        public String capture_method;
        public Map<String, String> metadata;
        public StripeNextAction next_action;
        public StripePaymentError last_payment_error;
    }

    static class StripeNextAction {
        public String type;
        public StripeRedirectToUrl redirect_to_url;
    }

    static class StripeRedirectToUrl {
        public String url;
    }

    static class StripePaymentError {
        public String type;
        public String code;
        public String decline_code;
        public String message;
        public StripePaymentResponse payment_intent;
    }

    static class StripeErrorResponse {
        public StripePaymentError error;
    }
}
