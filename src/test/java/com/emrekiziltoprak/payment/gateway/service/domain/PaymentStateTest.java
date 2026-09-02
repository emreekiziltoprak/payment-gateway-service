package com.emrekiziltoprak.payment.gateway.service.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPaymentWithStatus;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentStateTest {

    // 1. Başarılı/Geçerli geçişleri test eden metod
    @ParameterizedTest(name = "{index} => {0} -> {1} should be valid ")
    @MethodSource("validTransitions")
    void shouldAllowValidTransitions(PaymentStatus from, PaymentStatus to) {
        Payment payment = aPaymentWithStatus(from);
        assertDoesNotThrow(() -> doTransition(payment, to));
        assertThat(payment.getStatus()).isEqualTo(to);
    }

    static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(INITIATED, PROCESSING),
                Arguments.of(INITIATED, AUTHORIZED),
                Arguments.of(INITIATED, CAPTURED),
                Arguments.of(INITIATED, FAILED),
                Arguments.of(PROCESSING, AUTHORIZED),
                Arguments.of(AUTHORIZED, CAPTURED),
                Arguments.of(PROCESSING, CAPTURED),
                Arguments.of(REQUIRES_ACTION, PROCESSING),
                Arguments.of(REQUIRES_ACTION, AUTHORIZED),
                Arguments.of(REQUIRES_ACTION, CAPTURED),
                Arguments.of(PROCESSING, REQUIRES_ACTION),
                Arguments.of(AUTHORIZED, CANCELLED),
                Arguments.of(PROCESSING, FAILED),
                Arguments.of(REQUIRES_ACTION, FAILED),
                Arguments.of(AUTHORIZED, FAILED)
        );
    }

    @ParameterizedTest(name = "{index} => {0} -> {1} should be rejected ")
    @MethodSource("invalidTransitions")
    void shouldRejectInvalidTransitions(PaymentStatus from, PaymentStatus to) {
        Payment payment = aPaymentWithStatus(from);

        assertThrows(IllegalStateException.class, () -> doTransition(payment, to));

        assertThat(payment.getStatus()).isEqualTo(from);
        assertThat(payment.getDomainEvents()).isEmpty();
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(FAILED, CAPTURED),
                Arguments.of(CANCELLED, AUTHORIZED),

                Arguments.of(CAPTURED, AUTHORIZED),
                Arguments.of(CAPTURED, PROCESSING),
                Arguments.of(CAPTURED, REQUIRES_ACTION),
                Arguments.of(CAPTURED, CANCELLED),

                Arguments.of(FAILED, AUTHORIZED),
                Arguments.of(FAILED, PROCESSING),
                Arguments.of(FAILED, CAPTURED),
                Arguments.of(FAILED, CANCELLED),

                Arguments.of(CANCELLED, AUTHORIZED),
                Arguments.of(CANCELLED, PROCESSING),
                Arguments.of(CANCELLED, CAPTURED),
                Arguments.of(CANCELLED, FAILED),

                Arguments.of(REFUNDED, AUTHORIZED),
                Arguments.of(REFUNDED, CAPTURED),
                Arguments.of(PARTIALLY_REFUNDED, AUTHORIZED),
                Arguments.of(PARTIALLY_REFUNDED, CAPTURED),

                Arguments.of(INITIATED, REQUIRES_ACTION),
                Arguments.of(INITIATED, CANCELLED),
                Arguments.of(PROCESSING, CANCELLED),
                Arguments.of(REQUIRES_ACTION, CANCELLED)

        );
    }

    static void doTransition(
            Payment payment,
            PaymentStatus to
    ) {

        switch (to) {
            case AUTHORIZED -> payment.markAsAuthorized();
            case CAPTURED -> payment.markAsCaptured();
            case PROCESSING -> payment.markAsProcessing("Test processing reason");
            case REQUIRES_ACTION -> payment.markAsRequiredAction("Test action reason");
            case FAILED -> payment.markAsFailed("Test failure reason");
            case CANCELLED ->
                    payment.markAsCancelled(
                            PaymentCancellationReason.CUSTOMER_REQUESTED
                    );
            default -> throw new UnsupportedOperationException("Test helper does not support target status: " + to);
        }
    }

    //TODO:: TransitionResult.Idempotent
    @Test
    void shouldReturnIdempotentOnDuplicateCapture() {
        Payment payment = aPaymentWithStatus(CAPTURED);

        var result = payment.observeCapture(payment.getPaymentRef(), payment.getAmount(), Instant.now());
        assertThat(result).isInstanceOf(TransitionResult.Idempotent.class);
        assertThat(payment.getDomainEvents().size()).isEqualTo(0);
    }

    @Test
    void shouldNotEmitEventAndReturnStaleWhenAuthorizationArrivesAfterCapture() {
        Payment payment = aPaymentWithStatus(CAPTURED);
        Instant previousUpdateAt = payment.getUpdatedAt();

        var result = payment.observeAuthorization(payment.getPaymentRef(), previousUpdateAt.plusSeconds(60));
        assertThat(result).isInstanceOf(TransitionResult.Stale.class);
        assertThat(payment.getUpdatedAt()).isEqualTo(previousUpdateAt);
        assertThat(payment.getStatus()).isEqualTo(CAPTURED);
        assertThat(payment.getDomainEvents().size()).isEqualTo(0);
    }


    @Test
    void shouldReturnConflictOnConflictingCapture() {
        Payment payment = aPaymentWithStatus(CAPTURED);
        Money conflictingAmount = payment.getAmount().add(payment.getAmount());
        var result = payment.observeCapture(payment.getPaymentRef(), conflictingAmount, Instant.now());
        assertThat(result).isInstanceOf(TransitionResult.Conflict.class);
        assertThat(payment.getDomainEvents().size()).isEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("invalidInternalTransitions")
    void shouldRejectInvalidInternalTransitions(PaymentStatus initialStatus, Consumer<Payment> action) {
        Payment payment = aPaymentWithStatus(initialStatus);

        assertThrows(IllegalStateException.class, () -> action.accept(payment));
        assertThat(payment.getStatus()).isEqualTo(initialStatus);
    }

    static Stream<Arguments> invalidInternalTransitions() {
        return Stream.of(
                Arguments.of(
                        CAPTURED,
                        (Consumer<Payment>) payment -> payment.markAsAuthorized()
                ),
                Arguments.of(
                        CAPTURED,
                        (Consumer<Payment>) payment -> payment.markAsProcessing("reason")
                ),
                Arguments.of(
                        CAPTURED,
                        (Consumer<Payment>) payment -> payment.markAsRequiredAction("reason")
                ),
                Arguments.of(
                        CAPTURED,
                        (Consumer<Payment>) payment ->
                                payment.markAsCancelled(PaymentCancellationReason.CUSTOMER_REQUESTED)
                ),
                Arguments.of(
                        CAPTURED,
                        (Consumer<Payment>) payment -> payment.markAsFailed("error")
                )
        );
    }
}