package com.emrekiziltoprak.payment.gateway.service.ports.out;

public enum GatewayStatus {
    PROCESSING,
    REQUIRES_ACTION,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    CANCELLED,
    ERROR
}