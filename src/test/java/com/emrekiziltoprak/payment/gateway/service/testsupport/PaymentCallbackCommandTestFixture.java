package com.emrekiziltoprak.payment.gateway.service.testsupport;

import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.DEFAULT_PROVIDER;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.DEFAULT_PROVIDER_REFERENCE;

public final class PaymentCallbackCommandTestFixture {

    private PaymentCallbackCommandTestFixture() {
    }

    public static Builder aCallback() {
        return new Builder();
    }

    public static ProcessPaymentCallbackCommand aSuccessfulCallback() {
        return aCallback().successful().build();
    }

    public static ProcessPaymentCallbackCommand aFailedCallback(String reason) {
        return aCallback().failed(reason).build();
    }

    public static final class Builder {

        private PaymentProvider provider = DEFAULT_PROVIDER;
        private PaymentId paymentId;
        private String providerReference = DEFAULT_PROVIDER_REFERENCE;
        private ProcessPaymentCallbackCommand.CallbackStatus status =
                ProcessPaymentCallbackCommand.CallbackStatus.SUCCESS;
        private String failureReason;

        private Builder() {
        }

        public Builder withProvider(PaymentProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder withPaymentId(PaymentId paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder withProviderReference(String providerReference) {
            this.providerReference = providerReference;
            return this;
        }

        public Builder successful() {
            this.status = ProcessPaymentCallbackCommand.CallbackStatus.SUCCESS;
            this.failureReason = null;
            return this;
        }

        public Builder failed(String reason) {
            this.status = ProcessPaymentCallbackCommand.CallbackStatus.FAILED;
            this.failureReason = reason;
            return this;
        }

        public Builder withStatus(ProcessPaymentCallbackCommand.CallbackStatus status) {
            this.status = status;
            return this;
        }

        public Builder withFailureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public ProcessPaymentCallbackCommand build() {
            return new ProcessPaymentCallbackCommand(
                    provider,
                    paymentId,
                    providerReference,
                    status,
                    failureReason
            );
        }
    }
}
