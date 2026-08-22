package com.emrekiziltoprak.payment.gateway.service.adapters.out.gateway;

import com.emrekiziltoprak.payment.gateway.service.domain.GatewayStatus;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.GatewayTimeoutException;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component("iyzicoPaymentAdapter")
public class IyzicoPaymentAdapter implements PaymentGatewayPort {

    private final RestTemplate restTemplate;

    @Value("${iyzico.api.url:https://api.iyzipay.com/payment/auth}")
    private String iyzicoApiUrl;

    public IyzicoPaymentAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentGatewayResult processPayment(Payment payment) {
        try {
            IyzicoPaymentRequest request = mapToIyzicoRequest(payment);

            IyzicoPaymentResponse response = restTemplate.postForObject(
                    iyzicoApiUrl, request, IyzicoPaymentResponse.class
            );

            return mapToDomainResult(response);

        } catch (ResourceAccessException e) {
            throw new GatewayTimeoutException("Iyzico gateway timeout", e);
        } catch (HttpServerErrorException.GatewayTimeout e) {
            throw new GatewayTimeoutException("Iyzico gateway timeout", e);
        } catch (RestClientException e) {
            throw new GatewayTimeoutException("Iyzico gateway error", e);
        }
    }

    private IyzicoPaymentRequest mapToIyzicoRequest(Payment payment) {
        IyzicoPaymentRequest request = new IyzicoPaymentRequest();
        request.locale = "tr";
        request.conversationId = payment.getId().value().toString();
        request.price = payment.getAmount().amount().toString();
        request.paidPrice = payment.getAmount().amount().toString();
        request.currency = payment.getAmount().currency().getCurrencyCode();
        request.installment = 1;
        request.paymentChannel = "WEB";
        request.basketId = payment.getId().value().toString();
        request.paymentGroup = "PRODUCT";

        return request;
    }

    private PaymentGatewayResult mapToDomainResult(IyzicoPaymentResponse response) {
        if (response == null) {
            return new PaymentGatewayResult(
                    GatewayStatus.ERROR,
                    null,
                    "No response from Iyzico"
            );
        }

        GatewayStatus status;
        if ("success".equalsIgnoreCase(response.status) && response.fraudStatus == 1) {
            status = GatewayStatus.CAPTURED;
        } else if ("success".equalsIgnoreCase(response.status) && response.fraudStatus == 0) {
            status = GatewayStatus.DECLINED;
        } else {
            status = GatewayStatus.ERROR;
        }

        return new PaymentGatewayResult(
                status,
                response.paymentId,
                response.status + " (fraudStatus: " + response.fraudStatus + ")"
        );
    }

    static class IyzicoPaymentRequest {
        public String locale;
        public String conversationId;
        public String price;
        public String paidPrice;
        public String currency;
        public Integer installment;
        public String paymentChannel;
        public String basketId;
        public String paymentGroup;
    }

    static class IyzicoPaymentResponse {
        public String status;
        public String locale;
        public String conversationId;
        public String paymentId;
        public Integer fraudStatus; // 1 = approved, 0 = rejected
        public String price;
        public String paidPrice;
        public String currency;
        public String basketId;
        public List<ItemTransaction> itemTransactions;
    }

    static class ItemTransaction {
        public String itemId;
        public String paymentTransactionId;
        public String transactionStatus;
    }
}
