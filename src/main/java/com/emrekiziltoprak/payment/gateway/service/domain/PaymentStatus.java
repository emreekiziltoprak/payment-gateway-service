package com.emrekiziltoprak.payment.gateway.service.domain;

public enum PaymentStatus {
	INITIATED,
	AUTHORIZED,
	FAILED,
	//Instead of SUCCESS
	CAPTURED,
	//Instead of PENDING
	PROCESSING,
	REQUIRES_ACTION,
	//NEWLY ADDED
	CANCELLED,
	REFUNDED,
	PARTIALLY_REFUNDED
}