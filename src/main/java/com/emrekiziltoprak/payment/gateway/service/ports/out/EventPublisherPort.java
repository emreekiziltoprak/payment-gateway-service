package com.emrekiziltoprak.payment.gateway.service.ports.out;

import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentEvent;

import java.util.List;

public interface EventPublisherPort {
    void publish(List<PaymentEvent> events);
}
