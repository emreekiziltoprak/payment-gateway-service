package com.emrekiziltoprak.payment.gateway.service.ports.out;

import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;

public interface PaymentGatewayPort {
    PaymentLifecycleObservation processPayment(Payment payment);
}
