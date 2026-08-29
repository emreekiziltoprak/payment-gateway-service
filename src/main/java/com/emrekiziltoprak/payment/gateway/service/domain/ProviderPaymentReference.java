package com.emrekiziltoprak.payment.gateway.service.domain;

import java.util.Objects;

public record ProviderPaymentReference(PaymentProvider provider, String value) {
    public ProviderPaymentReference {
        Objects.requireNonNull(provider, "provider cant be null");
        if(value != null && value.isBlank()) {
            throw new IllegalArgumentException("reference cant be blank");
        }
    }

    public boolean hasValue() {
        return value != null && !value.isBlank();
    }
}
