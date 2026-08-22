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

import java.util.HashMap;
import java.util.Map;

@Component("stripePaymentAdapter")
public class StripePaymentAdapter implements PaymentGatewayPort {

    private final RestTemplate restTemplate;

    @Value("${stripe.api.url:https://api.stripe.com/v1/payment_intents}")
    private String stripeApiUrl;

    public StripePaymentAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentGatewayResult processPayment(Payment payment) {
        try {
            StripePaymentRequest request = mapToStripeRequest(payment);

            StripePaymentResponse response = restTemplate.postForObject(
                    stripeApiUrl, request, StripePaymentResponse.class
            );

            return mapToDomainResult(response);

        } catch (ResourceAccessException e) {
            throw new GatewayTimeoutException("Stripe gateway timeout", e);
        } catch (HttpServerErrorException.GatewayTimeout e) {
            throw new GatewayTimeoutException("Stripe gateway timeout", e);
        } catch (RestClientException e) {
            throw new GatewayTimeoutException("Stripe gateway error", e);
        }
    }

    private StripePaymentRequest mapToStripeRequest(Payment payment) {
        StripePaymentRequest request = new StripePaymentRequest();
        request.amount = payment.getAmount().amount().multiply(new java.math.BigDecimal("100")).longValue();
        request.currency = payment.getAmount().currency().getCurrencyCode().toLowerCase();

        request.metadata = new HashMap<>();
        request.metadata.put("payment_id", payment.getId().value().toString());
        request.metadata.put("source_account", payment.getSourceAccountId().value().toString());
        request.metadata.put("destination_account", payment.getDestinationAccountId().value().toString());

        return request;
    }

    private PaymentGatewayResult mapToDomainResult(StripePaymentResponse response) {
        if (response == null) {
            return new PaymentGatewayResult(
                    GatewayStatus.ERROR,
                    null,
                    "No response from Stripe"
            );
        }

        GatewayStatus status = switch (response.status) {
            case "succeeded" -> GatewayStatus.CAPTURED;
            case "processing", "requires_action" -> GatewayStatus.PENDING;
            case "requires_payment_method", "requires_confirmation" -> GatewayStatus.PENDING;
            case "canceled" -> GatewayStatus.DECLINED;
            default -> GatewayStatus.ERROR;
        };

        return new PaymentGatewayResult(
                status,
                response.id,
                response.status
        );
    }

    // Inner DTO classes for Stripe API
    static class StripePaymentRequest {
        public Long amount;
        public String currency;
        public Map<String, String> metadata;
    }

    static class StripePaymentResponse {
        public String id;
        public Long amount;
        public String currency;
        public String status;
        public String client_secret;
        public Map<String, String> metadata;
    }
}