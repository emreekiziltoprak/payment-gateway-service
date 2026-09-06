package com.emrekiziltoprak.payment.gateway.service.domain.lifecycle;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.ProviderPaymentReference;

public record LifecycleObservationContext(
        Optional<PaymentId> internalPaymentId,
        ProviderPaymentReference providerReference,
        Money amount,
        Instant observedAt,
        Optional<Instant> providerOccurredAt
) {
    public LifecycleObservationContext {
        internalPaymentId = Objects.requireNonNull(
                internalPaymentId, "internalPaymentId cannot be null"
        );
        providerReference = Objects.requireNonNull(
                providerReference, "providerReference cannot be null"
        );
        if (!providerReference.hasValue()) {
            throw new IllegalArgumentException("providerReference must have a value");
        }

        amount = Objects.requireNonNull(amount, "amount cannot be null");
        observedAt = Objects.requireNonNull(observedAt, "observedAt cannot be null");
        providerOccurredAt = Objects.requireNonNull(
                providerOccurredAt, "providerOccurredAt cannot be null"
        );
    }

    public Instant effectiveOccurredAt() {
        return providerOccurredAt.orElse(observedAt);
    }
}