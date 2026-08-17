package domain.event;

import domain.PaymentId;
import java.time.Instant;

public record PaymentSucceeded(
    PaymentId paymentId,
    Instant occurredAt
) implements PaymentEvent {
}
