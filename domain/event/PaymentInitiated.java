package domain.event;

import domain.AccountId;
import domain.Money;
import domain.PaymentId;
import java.time.Instant;

public record PaymentInitiated(
    PaymentId paymentId,
    AccountId sourceAccountId,
    AccountId destinationAccountId,
    Money amount,
    Instant occurredAt
) implements PaymentEvent {
}
