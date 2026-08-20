package com.emrekiziltoprak.payment.gateway.service.domain.event;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;

import java.time.Instant;

public record PaymentPending(PaymentId paymentId, String reason, Instant occurredAt) implements PaymentEvent {
}
