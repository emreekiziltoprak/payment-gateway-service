package com.emrekiziltoprak.payment.gateway.service.domain.lifecycle;

import java.net.URI;

public record RequiredPaymentAction(
        String type,
        URI redirectUri
) {
    public RequiredPaymentAction {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("action type cannot be blank");
        }
        type = type.trim();
    }
}