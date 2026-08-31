package com.emrekiziltoprak.payment.gateway.service.domain;

import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentInitiated;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPayment;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.anInitiatedPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentConstructionTests {

    @Test
    void initiateCreatesNewPaymentAndEmitsInitiatedEvent() {
        Payment payment = anInitiatedPayment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(payment.getReferenceId()).isNull();
        assertThat(payment.getCreatedAt()).isEqualTo(payment.getUpdatedAt());
        assertThat(payment.getDomainEvents())
                .singleElement()
                .isInstanceOfSatisfying(PaymentInitiated.class, event -> {
                    assertThat(event.paymentId()).isEqualTo(payment.getId());
                    assertThat(event.sourceAccountId()).isEqualTo(payment.getSourceAccountId());
                    assertThat(event.destinationAccountId()).isEqualTo(payment.getDestinationAccountId());
                    assertThat(event.amount()).isEqualTo(payment.getAmount());
                    assertThat(event.occurredAt()).isEqualTo(payment.getCreatedAt());
                });
    }

    @Test
    void restorePreservesPersistedStateWithoutEmittingEvents() {
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-02T12:30:00Z");

        Payment payment = aPayment()
                .withProviderReference("pi_restored_123")
                .withStatus(PaymentStatus.PROCESSING)
                .withTimestamps(createdAt, updatedAt)
                .buildRestored();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.getReferenceId()).isEqualTo("pi_restored_123");
        assertThat(payment.getCreatedAt()).isEqualTo(createdAt);
        assertThat(payment.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(payment.getDomainEvents()).isEmpty();
    }

    @Test
    void providerReferenceCanBeAssignedButNotReplaced() {
        Payment payment = anInitiatedPayment();

        payment.assignProviderReference("pi_test_123");
        payment.assignProviderReference("pi_test_123");

        assertThat(payment.getReferenceId()).isEqualTo("pi_test_123");
        assertThatThrownBy(() -> payment.assignProviderReference("pi_other_456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be replaced");
    }

    @Test
    void aggregateDoesNotExposePublicConstructorsOrGenericSetters() {
        Set<String> publicMethodNames = Arrays.stream(Payment.class.getMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertThat(Payment.class.getConstructors()).isEmpty();
        assertThat(publicMethodNames).doesNotContain(
                "setStatus",
                "setReferenceId",
                "setAmount",
                "setCreatedAt",
                "setUpdatedAt"
        );
    }
}
