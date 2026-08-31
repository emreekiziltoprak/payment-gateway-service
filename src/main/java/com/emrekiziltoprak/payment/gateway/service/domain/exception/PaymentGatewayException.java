package com.emrekiziltoprak.payment.gateway.service.domain.exception;

public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message) {
        super(message);
    }
}
