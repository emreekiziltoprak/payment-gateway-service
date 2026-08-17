package domain.event;

import domain.PaymentId;
import java.time.Instant;

public sealed interface PaymentEvent 
    permits PaymentInitiated, PaymentSucceeded, PaymentFailed, PaymentRefunded {
    
    PaymentId paymentId();
    Instant occurredAt();
}
