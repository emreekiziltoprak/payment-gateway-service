package com.emrekiziltoprak.payment.gateway.service.domain;

public enum PaymentStatus {
	INITIATED,
	PENDING,
	AUTHORIZED,
	SUCCEEDED,
	FAILED,
	REFUNDED
}