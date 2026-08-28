package com.emrekiziltoprak.payment.gateway.service.domain.exception;

public class InvalidMoneyException extends DomainRuleException {
    public InvalidMoneyException(String message){
        super(message);
    }
}
