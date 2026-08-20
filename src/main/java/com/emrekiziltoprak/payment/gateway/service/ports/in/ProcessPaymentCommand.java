package com.emrekiziltoprak.payment.gateway.service.ports.in;

import com.emrekiziltoprak.payment.gateway.service.domain.AccountId;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;

import java.math.BigDecimal;
import java.util.Currency;

public record ProcessPaymentCommand(
        AccountId sourceAccountId,
        AccountId destinationAccountId,
        Money amount,
        String idempotencyKey
        ) {}