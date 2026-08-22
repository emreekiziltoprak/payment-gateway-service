package com.emrekiziltoprak.payment.gateway.service.config;

import com.emrekiziltoprak.payment.gateway.service.application.ProcessPaymentService;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentUseCase;
import com.emrekiziltoprak.payment.gateway.service.ports.out.IdempotencyRepository;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
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

}
