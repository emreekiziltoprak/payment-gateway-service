package com.emrekiziltoprak.payment.gateway.service.domain.event;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import java.time.Instant;

public record PaymentSucceeded(
		PaymentId paymentId,
		Instant occurredAt) implements PaymentEvent {
}
