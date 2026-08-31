package com.emrekiziltoprak.payment.gateway.service.testsupport;

import com.emrekiziltoprak.payment.gateway.service.domain.AccountId;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

public final class PaymentTestFixture {

    public static final String DEFAULT_PROVIDER_REFERENCE = "pi_test_123";
    public static final Money DEFAULT_AMOUNT = new Money(
            new BigDecimal("25.00"),
            Currency.getInstance("TRY")
    );
    public static final PaymentProvider DEFAULT_PROVIDER = PaymentProvider.STRIPE;
    public static final Instant DEFAULT_CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    public static final Instant DEFAULT_UPDATED_AT = Instant.parse("2026-01-01T10:05:00Z");

    private PaymentTestFixture() {
    }

    public static Builder aPayment() {
        return new Builder();
    }

    public static Payment anInitiatedPayment() {
        return aPayment().buildInitiated();
    }

    public static Payment aPendingPayment() {
        return aPayment().withStatus(PaymentStatus.PROCESSING).buildRestored();
    }

    public static Payment aPaymentWithStatus(PaymentStatus status) {
        return aPayment().withStatus(status).buildRestored();
    }

    public static final class Builder {

        private PaymentId paymentId = PaymentId.generate();
        private AccountId sourceAccountId = AccountId.generate();
        private AccountId destinationAccountId = AccountId.generate();
        private String providerReference = DEFAULT_PROVIDER_REFERENCE;
        private Money amount = DEFAULT_AMOUNT;
        private PaymentProvider provider = DEFAULT_PROVIDER;
        private PaymentStatus status = PaymentStatus.PROCESSING;
        private Instant createdAt = DEFAULT_CREATED_AT;
        private Instant updatedAt = DEFAULT_UPDATED_AT;

        private Builder() {
        }

        public Builder withPaymentId(PaymentId paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder withSourceAccountId(AccountId sourceAccountId) {
            this.sourceAccountId = sourceAccountId;
            return this;
        }

        public Builder withDestinationAccountId(AccountId destinationAccountId) {
            this.destinationAccountId = destinationAccountId;
            return this;
        }

        public Builder withProviderReference(String providerReference) {
            this.providerReference = providerReference;
            return this;
        }

        public Builder withoutProviderReference() {
            this.providerReference = null;
            return this;
        }

        public Builder withAmount(Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder withProvider(PaymentProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder withStatus(PaymentStatus status) {
            this.status = status;
            return this;
        }

        public Builder withTimestamps(Instant createdAt, Instant updatedAt) {
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            return this;
        }

        public Payment buildInitiated() {
            return Payment.initiate(
                    paymentId,
                    sourceAccountId,
                    destinationAccountId,
                    amount,
                    provider
            );
        }

        public Payment buildRestored() {
            return Payment.restore(
                    paymentId,
                    sourceAccountId,
                    destinationAccountId,
                    providerReference,
                    amount,
                    provider,
                    status,
                    createdAt,
                    updatedAt
            );
        }
    }
}
