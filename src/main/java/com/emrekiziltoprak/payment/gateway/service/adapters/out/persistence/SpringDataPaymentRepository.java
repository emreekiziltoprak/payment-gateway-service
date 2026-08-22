package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataPaymentRepository extends JpaRepository<PaymentEntity, UUID> {
}