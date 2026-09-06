package com.emrekiziltoprak.payment.gateway.service.domain.lifecycle;

import java.util.Objects;

public record ProcessingObservation(
        LifecycleObservationContext context
) implements PaymentLifecycleObservation {
    public ProcessingObservation {
        context = Objects.requireNonNull(context, "context cannot be null");
    }
}