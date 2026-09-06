package com.emrekiziltoprak.payment.gateway.service.domain.lifecycle;

import java.util.Objects;

public record AuthorizationObservation(
        LifecycleObservationContext context
) implements PaymentLifecycleObservation {
    public AuthorizationObservation {
        context = Objects.requireNonNull(context, "context cannot be null");
    }
}