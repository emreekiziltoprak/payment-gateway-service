package com.emrekiziltoprak.payment.gateway.service.ports.out;

import com.emrekiziltoprak.payment.gateway.service.domain.IdempotencyRecord;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentEvent;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    void save(Payment payment);
    void saveInitiatedPaymentWithIdempotencyKey(Payment payment, IdempotencyRecord idempotencyRecord);
    Optional<Payment> findById(PaymentId paymentId);
    void saveStateAndOutbox(Payment payment, List<PaymentEvent> events);
}
