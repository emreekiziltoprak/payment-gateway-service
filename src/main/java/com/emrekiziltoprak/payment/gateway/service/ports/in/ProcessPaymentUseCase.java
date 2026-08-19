package com.emrekiziltoprak.payment.gateway.service.ports.in;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;

public interface ProcessPaymentUseCase {
    PaymentId processPayment(ProcessPaymentCommand command);
}
