package com.emrekiziltoprak.payment.gateway.service.domain.lifecycle;

import java.util.Objects;

public record CancellationObservation(
        LifecycleObservationContext context,
        PaymentCancellation cancellation
) implements PaymentLifecycleObservation {
    public CancellationObservation {
        context = Objects.requireNonNull(context, "context cannot be null");
        context.requireProviderReference();
        cancellation = Objects.requireNonNull(
                cancellation, "cancellation cannot be null"
        );
    }
}
