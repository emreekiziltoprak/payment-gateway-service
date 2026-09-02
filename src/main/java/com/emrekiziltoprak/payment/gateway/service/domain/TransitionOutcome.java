package com.emrekiziltoprak.payment.gateway.service.domain;

public enum TransitionOutcome {
    APPLIED,
    IDEMPOTENT_NO_OP,
    STALE_IGNORED,
    CONFLICT
}
