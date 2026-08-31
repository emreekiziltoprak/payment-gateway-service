package com.emrekiziltoprak.payment.gateway.service.ports.in;

import java.util.Objects;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;

public record ProcessPaymentCallbackCommand(
        PaymentProvider provider,
        PaymentId paymentId,
        String paymentReference,
        CallbackStatus status,
        PaymentFailure failure
) {

    public ProcessPaymentCallbackCommand(
            PaymentProvider provider,
            String paymentReference,
            CallbackStatus status,
            String failureReason) {
        this(provider, null, paymentReference, status, legacyFailure(status, failureReason));
    }

    public ProcessPaymentCallbackCommand(
            PaymentProvider provider,
            PaymentId paymentId,
            String paymentReference,
            CallbackStatus status,
            String failureReason) {
        this(provider, paymentId, paymentReference, status, legacyFailure(status, failureReason));
    }

    public ProcessPaymentCallbackCommand {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("paymentReference cannot be blank");
        }
        if (status == CallbackStatus.FAILED && failure == null) {
            failure = PaymentFailure.of(
                    PaymentFailureCode.GATEWAY_ERROR,
                    "Provider reported a payment failure without error details"
            );
        }
    }

    public String failureReason() {
        return failure != null ? failure.detail() : null;
    }

    private static PaymentFailure legacyFailure(CallbackStatus status, String failureReason) {
        if (status != CallbackStatus.FAILED || failureReason == null) {
            return null;
        }
        return PaymentFailure.of(PaymentFailureCode.DECLINED, failureReason);
    }

    public enum CallbackStatus {
        CAPTURED,
        CAPTURABLE,
        FAILED,
        REQUIRES_ACTION,
        PROCESSING,
        CANCELED
    }
}
