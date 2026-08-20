package com.emrekiziltoprak.payment.gateway.service.ports.out;

import com.emrekiziltoprak.payment.gateway.service.domain.Payment;

public interface PaymentGatewayPort {
    PaymentGatewayResult processPayment(Payment payment);
}
