package com.emrekiziltoprak.payment.gateway.service.ports.in;

   public interface ProcessPaymentCallbackUseCase {
   void processCallback(ProcessPaymentCallbackCommand command);
}
