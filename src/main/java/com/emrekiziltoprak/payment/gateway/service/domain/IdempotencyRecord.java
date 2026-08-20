package com.emrekiziltoprak.payment.gateway.service.domain;

import java.time.Instant;

public record IdempotencyRecord(
        String key,
        PaymentId paymentId,
        Instant createdAt
) {
    public IdempotencyRecord {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency key cannot be null or empty");
        }
        if (paymentId == null) {
            throw new IllegalArgumentException("PaymentId cannot be null");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}