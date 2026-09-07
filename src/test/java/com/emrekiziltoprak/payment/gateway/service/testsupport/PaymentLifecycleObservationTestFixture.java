package com.emrekiziltoprak.payment.gateway.service.testsupport;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentCancellationReason;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.ProviderPaymentReference;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ActionRequiredObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.AuthorizationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CancellationObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.CaptureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.FailureObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.LifecycleObservationContext;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentCancellation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.ProcessingObservation;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.RequiredPaymentAction;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.DEFAULT_AMOUNT;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.DEFAULT_PROVIDER;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.DEFAULT_PROVIDER_REFERENCE;

public final class PaymentLifecycleObservationTestFixture {

    public static final Instant DEFAULT_OBSERVED_AT = Instant.parse("2026-01-01T10:10:00Z");
    public static final Instant DEFAULT_PROVIDER_OCCURRED_AT = Instant.parse("2026-01-01T10:09:00Z");

    private PaymentLifecycleObservationTestFixture() {
    }

    public static Builder anObservation() {
        return new Builder();
    }

    public static CaptureObservation aCaptureObservation() {
        return anObservation().capture();
    }

    public static FailureObservation aFailureObservation(String detail) {
        return anObservation().failure(
                PaymentFailure.of(PaymentFailureCode.DECLINED, detail)
        );
    }

    public static final class Builder {

        private Optional<PaymentId> internalPaymentId = Optional.empty();
        private PaymentProvider provider = DEFAULT_PROVIDER;
        private String providerReference = DEFAULT_PROVIDER_REFERENCE;
        private Money amount = DEFAULT_AMOUNT;
        private Instant observedAt = DEFAULT_OBSERVED_AT;
        private Optional<Instant> providerOccurredAt = Optional.of(DEFAULT_PROVIDER_OCCURRED_AT);

        private Builder() {
        }

        public Builder withInternalPaymentId(PaymentId paymentId) {
            this.internalPaymentId = Optional.of(paymentId);
            return this;
        }

        public Builder withoutProviderReference() {
            this.providerReference = null;
            return this;
        }

        public Builder withProviderReference(String providerReference) {
            this.providerReference = providerReference;
            return this;
        }

        public Builder withProvider(PaymentProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder withAmount(Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder withObservedAt(Instant observedAt) {
            this.observedAt = observedAt;
            return this;
        }

        public Builder withProviderOccurredAt(Instant providerOccurredAt) {
            this.providerOccurredAt = Optional.of(providerOccurredAt);
            return this;
        }

        public Builder withoutProviderOccurredAt() {
            this.providerOccurredAt = Optional.empty();
            return this;
        }

        public LifecycleObservationContext context() {
            return new LifecycleObservationContext(
                    internalPaymentId,
                    new ProviderPaymentReference(provider, providerReference),
                    amount,
                    observedAt,
                    providerOccurredAt
            );
        }

        public CaptureObservation capture() {
            return new CaptureObservation(context());
        }

        public ProcessingObservation processing() {
            return new ProcessingObservation(context());
        }

        public AuthorizationObservation authorization() {
            return new AuthorizationObservation(context());
        }

        public ActionRequiredObservation actionRequired(String actionType, String redirectUrl) {
            return new ActionRequiredObservation(
                    context(),
                    new RequiredPaymentAction(
                            actionType,
                            redirectUrl == null ? null : URI.create(redirectUrl)
                    )
            );
        }

        public FailureObservation failure(PaymentFailure failure) {
            return new FailureObservation(context(), failure);
        }

        public CancellationObservation cancellation(PaymentCancellationReason reason) {
            return new CancellationObservation(
                    context(),
                    new PaymentCancellation(reason, null, null)
            );
        }
    }
}
