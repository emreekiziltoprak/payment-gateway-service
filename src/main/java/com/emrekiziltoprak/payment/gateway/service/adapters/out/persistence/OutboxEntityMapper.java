package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.OutboxEntity;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Component
public class OutboxEntityMapper {

    private final JsonMapper jsonMapper;

    public OutboxEntityMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public OutboxEntity toEntity(PaymentEvent event) {
        try {
            return OutboxEntity.builder()
                    .id(UUID.randomUUID())
                    .eventType(event.getClass().getSimpleName())
                    .payload(jsonMapper.writeValueAsString(event))
                    .createdAt(event.occurredAt())
                    .build();
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to serialize payment event: " + event.getClass().getSimpleName(),
                    exception
            );
        }
    }
}
