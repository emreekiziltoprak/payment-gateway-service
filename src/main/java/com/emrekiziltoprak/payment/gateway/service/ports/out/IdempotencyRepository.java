package com.emrekiziltoprak.payment.gateway.service.ports.out;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;

import java.util.Optional;

public interface IdempotencyRepository {
    Optional<PaymentId> findPaymentIdByKey(String idempotencyKey);
    void save(String idempotencyKey, PaymentId paymentId);
}
