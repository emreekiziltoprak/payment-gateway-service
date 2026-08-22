package com.emrekiziltoprak.payment.gateway.service.ports.out;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository {
    List<OutboxMessage> findUnprocessedEvents(int limit);
    void markAsProcessed(UUID id);

}
