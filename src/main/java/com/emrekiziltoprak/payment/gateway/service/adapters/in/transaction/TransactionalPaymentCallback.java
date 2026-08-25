package com.emrekiziltoprak.payment.gateway.service.adapters.in.transaction;

import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackUseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalPaymentCallback implements ProcessPaymentCallbackUseCase {

    private final ProcessPaymentCallbackUseCase delegate;

    public TransactionalPaymentCallback(ProcessPaymentCallbackUseCase delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    @Transactional
    public void processCallback(ProcessPaymentCallbackCommand command) {
        delegate.processCallback(command);
    }
}
