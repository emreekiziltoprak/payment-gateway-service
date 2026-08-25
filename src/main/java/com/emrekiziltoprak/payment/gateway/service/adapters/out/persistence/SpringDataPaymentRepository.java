package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.PaymentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataPaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentEntity> findByPaymentProviderAndReferenceId(String paymentProvider, String referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentEntity> findByIdAndPaymentProvider(UUID id, String paymentProvider);

}
