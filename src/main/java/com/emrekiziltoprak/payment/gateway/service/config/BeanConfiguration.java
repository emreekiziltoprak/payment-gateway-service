package com.emrekiziltoprak.payment.gateway.service.config;

import com.emrekiziltoprak.payment.gateway.service.application.OutboxPublisherService;
import com.emrekiziltoprak.payment.gateway.service.application.ProcessPaymentService;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentUseCase;
import com.emrekiziltoprak.payment.gateway.service.ports.out.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class BeanConfiguration {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ProcessPaymentUseCase processPaymentUseCase(
            PaymentRepository paymentRepository,
            PaymentGatewayPort paymentGatewayPort,
            IdempotencyRepository idempotencyRepository
    ) {
        return new ProcessPaymentService(paymentRepository, paymentGatewayPort, idempotencyRepository);
    }

    @Bean
    public OutboxPublisherService outboxPublisherService(
            OutboxRepository outboxRepository,
            EventPublisherPort eventPublisherPort
    ) {
        return new OutboxPublisherService(outboxRepository, eventPublisherPort);
    }

}
