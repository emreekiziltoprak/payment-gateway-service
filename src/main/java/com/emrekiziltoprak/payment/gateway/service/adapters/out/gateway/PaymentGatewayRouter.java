package com.emrekiziltoprak.payment.gateway.service.adapters.out.gateway;

import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@Primary
public class PaymentGatewayRouter implements PaymentGatewayPort {
    Map<String, PaymentGatewayPort> gatewayAdapters;


    public PaymentGatewayRouter(Map<String, PaymentGatewayPort> paymentGateways) {
        this.gatewayAdapters = paymentGateways.entrySet().stream()
                .filter(entry -> entry.getValue() != this)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    @Override
    public PaymentGatewayResult processPayment(Payment payment) {
        String beanName = payment.getPaymentProvider().name().toLowerCase() + "PaymentAdapter";

        PaymentGatewayPort adapter = gatewayAdapters.get(beanName);

        if(adapter == null) throw new IllegalArgumentException("Payment provider not supported");

        return adapter.processPayment(payment);
    }
}
