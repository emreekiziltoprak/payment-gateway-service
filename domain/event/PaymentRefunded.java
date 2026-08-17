package domain.event;

import domain.PaymentId;
import java.time.Instant;

public record PaymentRefunded(
    PaymentId paymentId,
    Instant occurredAt
) implements PaymentEvent {
}
