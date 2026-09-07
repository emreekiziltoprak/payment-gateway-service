package com.emrekiziltoprak.payment.gateway.service.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentFailed;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentRequiresAction;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentLifecycleObservationTestFixture.DEFAULT_PROVIDER_OCCURRED_AT;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentLifecycleObservationTestFixture.anObservation;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPayment;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentLifecycleObservationTests {

    @Test
    void captureObservationRejectsMismatchedIdentityReferenceAndAmount() {
        Payment payment = aPayment().buildRestored();
        Money mismatchedAmount = Money.of(new BigDecimal("30.00"), "TRY");

        TransitionResult result = payment.observe(
                anObservation()
                        .withInternalPaymentId(PaymentId.generate())
                        .withProviderReference("pi_different")
                        .withAmount(mismatchedAmount)
                        .capture()
        );

        assertThat(result).isInstanceOfSatisfying(
                TransitionResult.Conflict.class,
                conflict -> assertThat(conflict.details())
                        .anyMatch(detail -> detail.startsWith("Payment ID mismatch"))
                        .anyMatch(detail -> detail.startsWith("Provider reference mismatch"))
                        .anyMatch(detail -> detail.startsWith("Amount mismatch"))
        );
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.getPaymentRef().value()).isEqualTo("pi_test_123");
        assertThat(payment.getDomainEvents()).isEmpty();
    }

    @Test
    void observationFromAnotherProviderConflictsEvenWhenReferenceIsNotYetBound() {
        Payment payment = aPayment()
                .withoutProviderReference()
                .withStatus(PaymentStatus.PROCESSING)
                .buildRestored();

        TransitionResult result = payment.observe(
                anObservation()
                        .withInternalPaymentId(payment.getId())
                        .withProvider(PaymentProvider.IYZICO)
                        .withProviderReference("iyzi_payment_123")
                        .processing()
        );

        assertThat(result).isInstanceOfSatisfying(
                TransitionResult.Conflict.class,
                conflict -> assertThat(conflict.details())
                        .anyMatch(detail -> detail.startsWith("Provider mismatch"))
        );
        assertThat(payment.getPaymentRef().provider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(payment.getPaymentRef().hasValue()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.getDomainEvents()).isEmpty();
    }

    @Test
    void failureWithoutProviderReferenceAppliesByInternalIdAndPreservesStructuredFailure() {
        Payment payment = aPayment()
                .withoutProviderReference()
                .withStatus(PaymentStatus.PROCESSING)
                .buildRestored();
        PaymentFailure failure = PaymentFailure.fromProvider(
                PaymentFailureCode.DECLINED,
                "card_declined",
                "insufficient_funds",
                "The card has insufficient funds"
        );

        TransitionResult result = payment.observe(
                anObservation()
                        .withInternalPaymentId(payment.getId())
                        .withoutProviderReference()
                        .failure(failure)
        );

        assertThat(result).isInstanceOf(TransitionResult.Applied.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailure()).isEqualTo(failure);
        assertThat(payment.getPaymentRef().hasValue()).isFalse();
        assertThat(payment.getUpdatedAt()).isEqualTo(DEFAULT_PROVIDER_OCCURRED_AT);
        assertThat(payment.getDomainEvents())
                .singleElement()
                .isInstanceOfSatisfying(
                        PaymentFailed.class,
                        event -> assertThat(event.failure()).isEqualTo(failure)
                );
    }

    @Test
    void nonRedirectActionUsesActionTypeAsDomainDescription() {
        Payment payment = aPayment()
                .withStatus(PaymentStatus.PROCESSING)
                .buildRestored();

        TransitionResult result = payment.observe(
                anObservation()
                        .withInternalPaymentId(payment.getId())
                        .actionRequired("use_stripe_sdk", null)
        );

        assertThat(result).isInstanceOf(TransitionResult.Applied.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(payment.getUpdatedAt()).isEqualTo(DEFAULT_PROVIDER_OCCURRED_AT);
        assertThat(payment.getDomainEvents())
                .singleElement()
                .isInstanceOfSatisfying(
                        PaymentRequiresAction.class,
                        event -> assertThat(event.reason()).isEqualTo("use_stripe_sdk")
                );
    }
}
