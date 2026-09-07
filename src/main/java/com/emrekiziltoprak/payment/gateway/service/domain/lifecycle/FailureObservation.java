package com.emrekiziltoprak.payment.gateway.service.domain.lifecycle;

import java.util.Objects;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;

public record FailureObservation(
        LifecycleObservationContext context,
        PaymentFailure failure
) implements PaymentLifecycleObservation {
    public FailureObservation {
        context = Objects.requireNonNull(context, "context cannot be null");
        failure = Objects.requireNonNull(failure, "failure cannot be null");
    }
}