package com.emrekiziltoprak.payment.gateway.service.domain;

public enum GatewayStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    DECLINED,
    ERROR
}
