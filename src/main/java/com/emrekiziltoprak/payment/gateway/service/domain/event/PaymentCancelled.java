package com.emrekiziltoprak.payment.gateway.service.domain.event;

import java.time.Instant;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentCancellationReason;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;

public record PaymentCancelled(PaymentId paymentId, PaymentCancellationReason reason, Instant occurredAt) implements PaymentEvent {

}
