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
        amount = Objects.requireNonNull(amount, "amount cannot be null");
        observedAt = Objects.requireNonNull(observedAt, "observedAt cannot be null");
        providerOccurredAt = Objects.requireNonNull(
                providerOccurredAt, "providerOccurredAt cannot be null"
        );

        if (internalPaymentId.isEmpty() && !providerReference.hasValue()) {
            throw new IllegalArgumentException(
                    "either internalPaymentId or provider reference is required"
            );
        }
    }

    public Instant effectiveOccurredAt() {
        return providerOccurredAt.orElse(observedAt);
    }

    void requireProviderReference() {
        if (!providerReference.hasValue()) {
            throw new IllegalArgumentException("provider reference value is required");
        }
    }
}
