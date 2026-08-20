package com.emrekiziltoprak.payment.gateway.service.domain.exception;

public class GatewayTimeoutException extends RuntimeException {

    public GatewayTimeoutException(String message) {
        super(message);
    }

    public GatewayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}