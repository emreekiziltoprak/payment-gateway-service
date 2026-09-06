package com.emrekiziltoprak.payment.gateway.service.domain.lifecycle;

import java.util.Objects;

public record CaptureObservation(
        LifecycleObservationContext context
) implements PaymentLifecycleObservation {
    public CaptureObservation {
        context = Objects.requireNonNull(context, "context cannot be null");
    }
}