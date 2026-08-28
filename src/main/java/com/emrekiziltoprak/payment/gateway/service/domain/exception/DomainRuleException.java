package com.emrekiziltoprak.payment.gateway.service.domain.exception;

public abstract class DomainRuleException extends RuntimeException {

    public DomainRuleException(String message) {
        super(message);
    }
}