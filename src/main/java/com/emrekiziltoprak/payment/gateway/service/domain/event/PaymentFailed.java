package com.emrekiziltoprak.payment.gateway.service.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;

public record PaymentFailed(
        PaymentId paymentId,
        PaymentFailure failure,
        Instant occurredAt
) implements PaymentEvent {

    public PaymentFailed {
        Objects.requireNonNull(paymentId, "paymentId cannot be null");
        Objects.requireNonNull(failure, "failure cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }

    public String reason() {
        return failure.detail();
    }
}
