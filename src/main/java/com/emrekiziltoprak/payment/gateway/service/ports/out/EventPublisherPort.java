package com.emrekiziltoprak.payment.gateway.service.ports.out;

public interface EventPublisherPort {
    void publish(OutboxMessage message);
}
