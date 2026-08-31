package com.emrekiziltoprak.payment.gateway.service.domain.event;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import java.time.Instant;

public record PaymentCaptured(
		PaymentId paymentId,
		Instant occurredAt) implements PaymentEvent {
}
