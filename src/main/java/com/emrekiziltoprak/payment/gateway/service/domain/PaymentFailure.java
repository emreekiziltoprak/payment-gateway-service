package com.emrekiziltoprak.payment.gateway.service.domain;

import java.util.Objects;

public record PaymentFailure(
        PaymentFailureCode code,
        String providerCode,
        String providerDeclineCode,
        String detail
) {
    public PaymentFailure {
        Objects.requireNonNull(code, "failure code cannot be null");
    }

    public static PaymentFailure of(PaymentFailureCode code, String detail) {
        return new PaymentFailure(code, null, null, detail);
    }

    public static PaymentFailure fromProvider(
            PaymentFailureCode code,
            String providerCode,
            String providerDeclineCode,
            String detail
    ) {
        return new PaymentFailure(code, providerCode, providerDeclineCode, detail);
    }
}
