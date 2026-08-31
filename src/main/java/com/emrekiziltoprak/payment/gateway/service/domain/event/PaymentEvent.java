package com.emrekiziltoprak.payment.gateway.service.domain.event;

import java.time.Instant;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;

public sealed interface PaymentEvent
		permits PaymentFailed, PaymentInitiated, PaymentRefunded, PaymentCaptured, PaymentProcessing, PaymentCancelled, PaymentRequiresAction{

	PaymentId paymentId();

	Instant occurredAt();
}
