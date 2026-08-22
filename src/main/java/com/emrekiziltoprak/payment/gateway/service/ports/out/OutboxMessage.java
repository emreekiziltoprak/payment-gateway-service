package com.emrekiziltoprak.payment.gateway.service.ports.out;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(
        UUID id,
        String eventType,
        String payload,
        Instant createdAt
) {
}