package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentSucceeded;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEntityMapperTests {

    private final OutboxEntityMapper mapper = new OutboxEntityMapper(new JsonMapper());

    @Test
    void serializesPaymentEventWithInstant() {
        Instant occurredAt = Instant.parse("2026-08-22T12:00:00Z");
        PaymentSucceeded event = new PaymentSucceeded(PaymentId.generate(), occurredAt);

        var entity = mapper.toEntity(event);

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getEventType()).isEqualTo("PaymentSucceeded");
        assertThat(entity.getPayload()).contains("\"occurredAt\":\"2026-08-22T12:00:00Z\"");
        assertThat(entity.getCreatedAt()).isEqualTo(occurredAt);
        assertThat(entity.getProcessedAt()).isNull();
    }
}
