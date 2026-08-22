package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataIdempotencyRepository extends JpaRepository<IdempotencyEntity, String> {
}
