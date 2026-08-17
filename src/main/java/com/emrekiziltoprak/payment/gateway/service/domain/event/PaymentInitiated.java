package com.emrekiziltoprak.payment.gateway.service.domain.event;

import com.emrekiziltoprak.payment.gateway.service.domain.AccountId;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import java.time.Instant;

public record PaymentInitiated(
		PaymentId paymentId,
		AccountId sourceAccountId,
		AccountId destinationAccountId,
		Money amount,
		Instant occurredAt) implements PaymentEvent {
}
