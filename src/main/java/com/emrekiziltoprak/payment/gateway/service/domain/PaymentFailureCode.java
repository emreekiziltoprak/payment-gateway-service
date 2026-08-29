package com.emrekiziltoprak.payment.gateway.service.domain;

public enum PaymentFailureCode {
    DECLINED,
    GATEWAY_ERROR,
    TIMEOUT,
    CANCELLED,
    VALIDATION_ERROR
}