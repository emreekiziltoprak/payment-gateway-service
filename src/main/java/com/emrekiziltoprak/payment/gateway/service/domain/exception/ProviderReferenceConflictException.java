package com.emrekiziltoprak.payment.gateway.service.domain.exception;

public class ProviderReferenceConflictException extends IllegalStateException {

    public ProviderReferenceConflictException(String message) {
        super(message);
    }
}
