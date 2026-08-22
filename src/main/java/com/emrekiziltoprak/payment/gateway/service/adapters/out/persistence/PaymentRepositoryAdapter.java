package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.IdempotencyEntity;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.OutboxEntity;
import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.PaymentEntity;
import com.emrekiziltoprak.payment.gateway.service.domain.IdempotencyRecord;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentEvent;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final SpringDataPaymentRepository paymentRepository;
    private final SpringDataIdempotencyRepository idempotencyRepository;
    private final SpringDataOutboxRepository outboxRepository;

    @Override
    @Transactional
    public void save(Payment payment) {
        paymentRepository.save(PaymentEntity.fromDomain(payment));
    }

    @Override
    @Transactional
    public void saveInitiatedPaymentWithIdempotencyKey(Payment payment, IdempotencyRecord idempotencyRecord) {
        paymentRepository.save(PaymentEntity.fromDomain(payment));
        idempotencyRepository.save(IdempotencyEntity.fromDomain(idempotencyRecord));
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        return paymentRepository.findById(paymentId.value())
                .map(PaymentEntity::toDomain);
    }

    @Override
    @Transactional
    public void saveStateAndOutbox(Payment payment, List<PaymentEvent> events) {
        paymentRepository.save(PaymentEntity.fromDomain(payment));
        events.forEach(event -> outboxRepository.save(OutboxEntity.fromDomain(event)));
    }
}
