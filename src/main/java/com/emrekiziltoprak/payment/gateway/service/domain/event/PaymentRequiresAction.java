package com.emrekiziltoprak.payment.gateway.service.domain.event;

import java.time.Instant;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;


public record PaymentRequiresAction(PaymentId paymentId, String reason, Instant occurredAt) implements PaymentEvent {

}
