package com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence;

import com.emrekiziltoprak.payment.gateway.service.adapters.out.persistence.entitites.OutboxEntity;
import com.emrekiziltoprak.payment.gateway.service.ports.out.OutboxMessage;
import com.emrekiziltoprak.payment.gateway.service.ports.out.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements OutboxRepository {

    private final SpringDataOutboxRepository outboxRepository;


    @Override
    public List<OutboxMessage> findUnprocessedEvents(int limit) {
        return outboxRepository
                .findByProcessedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, limit))
                .stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    @Transactional
    public void markAsProcessed(UUID id) {
        OutboxEntity outboxEntity = outboxRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException(
                        "Outbox event not found:" + id
                ));
        outboxEntity.markAsProcessed();
    }

    private OutboxMessage toMessage(OutboxEntity outboxEntity) {
        return new OutboxMessage(
                outboxEntity.getId(),
                outboxEntity.getEventType(),
                outboxEntity.getPayload(),
                outboxEntity.getCreatedAt()
        );
    }
}
