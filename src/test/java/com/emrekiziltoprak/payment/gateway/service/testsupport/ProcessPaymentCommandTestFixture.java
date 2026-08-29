package com.emrekiziltoprak.payment.gateway.service.testsupport;

import com.emrekiziltoprak.payment.gateway.service.domain.AccountId;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCommand;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.DEFAULT_AMOUNT;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.DEFAULT_PROVIDER;

public final class ProcessPaymentCommandTestFixture {

    public static final String DEFAULT_IDEMPOTENCY_KEY = "idempotency-key-123";

    private ProcessPaymentCommandTestFixture() {
    }

    public static ProcessPaymentCommand aProcessPaymentCommand() {
        return aProcessPaymentCommandBuilder().build();
    }

    public static Builder aProcessPaymentCommandBuilder() {
        return new Builder();
    }

    public static final class Builder {

        private AccountId sourceAccountId = AccountId.generate();
        private AccountId destinationAccountId = AccountId.generate();
        private Money amount = DEFAULT_AMOUNT;
        private String idempotencyKey = DEFAULT_IDEMPOTENCY_KEY;
        private PaymentProvider provider = DEFAULT_PROVIDER;

        private Builder() {
        }

        public Builder withSourceAccountId(AccountId sourceAccountId) {
            this.sourceAccountId = sourceAccountId;
            return this;
        }

        public Builder withDestinationAccountId(AccountId destinationAccountId) {
            this.destinationAccountId = destinationAccountId;
            return this;
        }

        public Builder withAmount(Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder withIdempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder withProvider(PaymentProvider provider) {
            this.provider = provider;
            return this;
        }

        public ProcessPaymentCommand build() {
            return new ProcessPaymentCommand(
                    sourceAccountId,
                    destinationAccountId,
                    amount,
                    idempotencyKey,
                    provider
            );
        }
    }
}
