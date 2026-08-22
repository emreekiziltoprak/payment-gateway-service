package com.emrekiziltoprak.payment.gateway.service.adapters.in.web;

import java.math.BigDecimal;

public record PaymentRequestDTO(
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        String provider
) {
}