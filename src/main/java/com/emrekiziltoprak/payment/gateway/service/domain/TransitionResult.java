package com.emrekiziltoprak.payment.gateway.service.domain;

import java.util.List;

public sealed interface TransitionResult {

    record Applied() implements TransitionResult {}

    record Idempotent() implements TransitionResult {}

    record Stale(String reason) implements TransitionResult {}

    record Conflict(List<String> details) implements TransitionResult {
        public Conflict(String detail) {
            this(List.of(detail));
        }
    }

}