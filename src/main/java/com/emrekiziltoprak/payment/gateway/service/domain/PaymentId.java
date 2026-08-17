package com.emrekiziltoprak.payment.gateway.service.domain;

import java.util.UUID;

public record PaymentId(UUID value) {

	public PaymentId {
		if (value == null) {
			throw new IllegalArgumentException("PaymentId cannot be null");
		}
	}

	public static PaymentId generate() {
		return new PaymentId(UUID.randomUUID());
	}
}
