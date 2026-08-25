package com.emrekiziltoprak.payment.gateway.service.ports.in;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;

import java.util.Objects;

public record ProcessPaymentCallbackCommand(
        PaymentProvider provider,
        PaymentId paymentId,
        String paymentReference,
        CallbackStatus status,
        String failureReason
) {

    public ProcessPaymentCallbackCommand(
            PaymentProvider provider,
            String paymentReference,
            CallbackStatus status,
            String failureReason) {
        this(provider, null, paymentReference, status, failureReason);
    }

    public ProcessPaymentCallbackCommand {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("paymentReference cannot be blank");
        }
    }

    public enum CallbackStatus {
        SUCCESS,
        FAILED,
        REQUIRES_ACTION,
        PROCESSING,
        CANCELED
    }
}
