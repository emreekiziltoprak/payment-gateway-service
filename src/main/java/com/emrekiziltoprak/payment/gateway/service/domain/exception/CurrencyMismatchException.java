package com.emrekiziltoprak.payment.gateway.service.domain.exception;

public class CurrencyMismatchException extends DomainRuleException {
    public CurrencyMismatchException(String message) {
        super(message);
    }
}
