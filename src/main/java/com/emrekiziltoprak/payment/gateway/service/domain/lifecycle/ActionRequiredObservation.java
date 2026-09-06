package com.emrekiziltoprak.payment.gateway.service.domain.lifecycle;

import java.util.Objects;

public record ActionRequiredObservation(
        LifecycleObservationContext context,
        RequiredPaymentAction action
) implements PaymentLifecycleObservation {
    public ActionRequiredObservation {
        context = Objects.requireNonNull(context, "context cannot be null");
        action = Objects.requireNonNull(action, "action cannot be null");
    }
}