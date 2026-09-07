package com.emrekiziltoprak.payment.gateway.service.domain.lifecycle;

import java.util.Objects;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentCancellationReason;

public record PaymentCancellation(
        PaymentCancellationReason reason,
        String providerReason,
        String detail
) {
    public PaymentCancellation {
        reason = Objects.requireNonNull(reason, "reason cannot be null");
        providerReason = blankToNull(providerReason);
        detail = blankToNull(detail);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}