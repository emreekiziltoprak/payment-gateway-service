package com.emrekiziltoprak.payment.gateway.service.adapters.in.web;


import com.emrekiziltoprak.payment.gateway.service.domain.AccountId;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;

    @PostMapping
    public ResponseEntity<PaymentResponseDTO> processPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequestDTO request
    ) {
        PaymentProvider provider = PaymentProvider.valueOf(request.provider().toUpperCase());

        ProcessPaymentCommand command = new ProcessPaymentCommand(
                new AccountId(UUID.fromString(request.sourceAccountId())),
                new AccountId(UUID.fromString(request.destinationAccountId())),
                new Money(request.amount(), Currency.getInstance(request.currency().toUpperCase())),
                idempotencyKey,
                provider
        );

        PaymentId paymentId = processPaymentUseCase.processPayment(command);

        PaymentResponseDTO response = new PaymentResponseDTO(
                paymentId.value().toString(),
                "INITIATED",
                "Payment process mapped to " + provider + " and initiated successfully."
        );

        return ResponseEntity.ok(response);
    }
}
