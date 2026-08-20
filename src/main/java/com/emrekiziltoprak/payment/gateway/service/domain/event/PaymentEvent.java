package com.emrekiziltoprak.payment.gateway.service.domain.event;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import java.time.Instant;

public sealed interface PaymentEvent
		permits PaymentFailed, PaymentInitiated, PaymentRefunded, PaymentSucceeded, PaymentPending {

	PaymentId paymentId();

	Instant occurredAt();
}
