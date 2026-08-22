package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEntity, UUID> {
}
