package com.emrekiziltoprak.payment.gateway.service.application;

import com.emrekiziltoprak.payment.gateway.service.ports.out.EventPublisherPort;
import com.emrekiziltoprak.payment.gateway.service.ports.out.OutboxMessage;
import com.emrekiziltoprak.payment.gateway.service.ports.out.OutboxRepository;

import java.util.List;

public class OutboxPublisherService {

    private final OutboxRepository outboxRepository;
    private final EventPublisherPort eventPublisherPort;

    public OutboxPublisherService(
            OutboxRepository outboxRepository,
            EventPublisherPort eventPublisherPort
    ) {
        this.outboxRepository = outboxRepository;
        this.eventPublisherPort = eventPublisherPort;
    }

    public int publishPendingEvents(int batchSize) {
        List<OutboxMessage> messages =
                outboxRepository.findUnprocessedEvents(batchSize);

        int publishedCount = 0;

        for (OutboxMessage message : messages) {
            eventPublisherPort.publish(message);
            outboxRepository.markAsProcessed(message.id());

            publishedCount++;
        }

        return publishedCount;
    }
}