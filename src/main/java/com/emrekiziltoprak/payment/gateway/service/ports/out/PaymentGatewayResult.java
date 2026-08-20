package com.emrekiziltoprak.payment.gateway.service.ports.out;

import com.emrekiziltoprak.payment.gateway.service.domain.GatewayStatus;

public record PaymentGatewayResult(
GatewayStatus status,
String transactionId,
String failureReason
) {}
