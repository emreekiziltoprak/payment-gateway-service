package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites;

import com.emrekiziltoprak.payment.gateway.service.domain.IdempotencyRecord;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyEntity {

    @Id
    @Column(name = "idempotency_key", updatable = false, nullable = false)
    private String idempotencyKey;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static IdempotencyEntity fromDomain(IdempotencyRecord record) {
        return IdempotencyEntity.builder()
                .idempotencyKey(record.key())
                .paymentId(record.paymentId().value())
                .createdAt(record.createdAt())
                .build();
    }

    public PaymentId toDomain() {
        return new PaymentId(paymentId);
    }
}
