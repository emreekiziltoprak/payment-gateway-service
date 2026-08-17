package domain.event;

import domain.PaymentId;
import java.time.Instant;

public record PaymentFailed(
    PaymentId paymentId,
    String reason,
    Instant occurredAt
) implements PaymentEvent {
}
