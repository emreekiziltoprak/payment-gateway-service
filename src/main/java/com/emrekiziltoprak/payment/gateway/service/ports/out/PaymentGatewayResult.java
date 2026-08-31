package com.emrekiziltoprak.payment.gateway.service.ports.out;


public record PaymentGatewayResult(
GatewayStatus status,
String transactionId,
String failureReason
) {}
