package com.emrekiziltoprak.payment.gateway.service.ports.in;

import com.emrekiziltoprak.payment.gateway.service.domain.AccountId;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;

public record ProcessPaymentCommand(
        AccountId sourceAccountId,
        AccountId destinationAccountId,
        Money amount,
        String idempotencyKey,
        PaymentProvider paymentProvider
        ) {}