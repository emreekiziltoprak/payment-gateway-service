package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites;

import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, length = 4000)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static OutboxEntity fromDomain(PaymentEvent event) {
        try {
            return OutboxEntity.builder()
                    .id(UUID.randomUUID())
                    .eventType(event.getClass().getSimpleName())
                    .payload(objectMapper.writeValueAsString(event))
                    .createdAt(event.occurredAt())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
