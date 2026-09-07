package com.emrekiziltoprak.payment.gateway.service.ports.in;

import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;

public interface ProcessPaymentCallbackUseCase {
   void processCallback(PaymentLifecycleObservation observation);
}
