package com.emrekiziltoprak.payment.gateway.service.domain.lifecycle;

public sealed interface PaymentLifecycleObservation permits
ProcessingObservation,
ActionRequiredObservation,
AuthorizationObservation,
CaptureObservation,
FailureObservation,
CancellationObservation {
    LifecycleObservationContext context();
}
