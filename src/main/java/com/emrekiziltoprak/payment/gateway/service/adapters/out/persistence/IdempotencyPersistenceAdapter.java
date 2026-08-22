package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.IdempotencyEntity;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.ports.out.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IdempotencyPersistenceAdapter implements IdempotencyRepository {

    private final SpringDataIdempotencyRepository jpaRepository;

    @Override
    public Optional<PaymentId> findPaymentIdByKey(String idempotencyKey) {
        return jpaRepository.findById(idempotencyKey)
                .map(entity -> new PaymentId(entity.getPaymentId()));
    }

    @Override
    public void save(String idempotencyKey, PaymentId paymentId) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setIdempotencyKey(idempotencyKey);
        entity.setPaymentId(paymentId.value());

        jpaRepository.save(entity);
    }
}
