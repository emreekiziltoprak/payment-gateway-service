package com.emrekiziltoprak.payment.gateway.service.domain.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
