package com.emrekiziltoprak.payment.gateway.service.adapters.out.gateway;

import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;

@Component
@Primary
public class PaymentGatewayRouter implements PaymentGatewayPort {
    private final Map<String, PaymentGatewayPort> gatewayAdapters;


    public PaymentGatewayRouter(Map<String, PaymentGatewayPort> paymentGateways) {
        this.gatewayAdapters = paymentGateways.entrySet().stream()
                .filter(entry -> entry.getValue() != this)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public PaymentLifecycleObservation processPayment(Payment payment) {
        String beanName = payment.getPaymentRef().provider().name()
                .toLowerCase(Locale.ROOT) + "PaymentAdapter";

        PaymentGatewayPort adapter = gatewayAdapters.get(beanName);

        if (adapter == null) {
            throw new IllegalArgumentException("Payment provider not supported");
        }

        return adapter.processPayment(payment);
    }
}
