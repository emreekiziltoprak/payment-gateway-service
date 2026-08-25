package com.emrekiziltoprak.payment.gateway.service.application;

import com.emrekiziltoprak.payment.gateway.service.domain.AccountId;
import com.emrekiziltoprak.payment.gateway.service.domain.Money;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentProvider;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentFailed;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentSucceeded;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.out.IdempotencyRepository;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessPaymentCallbackServiceTests {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final ProcessPaymentService service = new ProcessPaymentService(
            paymentRepository,
            mock(PaymentGatewayPort.class),
            mock(IdempotencyRepository.class)
    );

    @Test
    void succeedsPendingPaymentAndSavesOutboxEvent() {
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByProviderAndReferenceIdForUpdate(
                PaymentProvider.STRIPE, "pi_test_123"
        )).thenReturn(Optional.of(payment));

        service.processCallback(new ProcessPaymentCallbackCommand(
                PaymentProvider.STRIPE,
                "pi_test_123",
                ProcessPaymentCallbackCommand.CallbackStatus.SUCCESS,
                null
        ));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).saveStateAndOutbox(
                same(payment),
                argThat(events -> events.size() == 1 && events.getFirst() instanceof PaymentSucceeded)
        );
    }

    @Test
    void failsPendingPaymentAndSavesOutboxEvent() {
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByProviderAndReferenceIdForUpdate(
                PaymentProvider.STRIPE, "pi_test_123"
        )).thenReturn(Optional.of(payment));

        service.processCallback(new ProcessPaymentCallbackCommand(
                PaymentProvider.STRIPE,
                "pi_test_123",
                ProcessPaymentCallbackCommand.CallbackStatus.FAILED,
                "declined"
        ));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).saveStateAndOutbox(
                same(payment),
                argThat(events -> events.size() == 1
                        && events.getFirst() instanceof PaymentFailed failed
                        && failed.reason().equals("declined"))
        );
    }

    @Test
    void ignoresDuplicateTerminalCallbackWithoutCreatingAnotherOutboxEvent() {
        Payment payment = paymentWithStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByProviderAndReferenceIdForUpdate(
                PaymentProvider.STRIPE, "pi_test_123"
        )).thenReturn(Optional.of(payment));

        service.processCallback(new ProcessPaymentCallbackCommand(
                PaymentProvider.STRIPE,
                "pi_test_123",
                ProcessPaymentCallbackCommand.CallbackStatus.SUCCESS,
                null
        ));

        verify(paymentRepository, never()).saveStateAndOutbox(
                same(payment),
                argThat(events -> true)
        );
    }

    @Test
    void rejectsCallbackForMismatchedTerminalStatePayment() {
        // Payment already SUCCEEDED, but callback says FAILED
        Payment payment = paymentWithStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByProviderAndReferenceIdForUpdate(
                PaymentProvider.STRIPE, "pi_test_123"
        )).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.processCallback(new ProcessPaymentCallbackCommand(
                PaymentProvider.STRIPE,
                "pi_test_123",
                ProcessPaymentCallbackCommand.CallbackStatus.FAILED,
                null
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal state");

        verify(paymentRepository, never()).saveStateAndOutbox(
                same(payment),
                argThat(events -> true)
        );
    }

    @Test
    void resolvesTimedOutPaymentByInternalIdAndStoresGatewayReference() {
        Payment payment = new Payment(
                PaymentId.generate(),
                AccountId.generate(),
                AccountId.generate(),
                null,
                new Money(new BigDecimal("25.00"), Currency.getInstance("TRY")),
                PaymentProvider.STRIPE,
                PaymentStatus.PENDING
        );
        when(paymentRepository.findByProviderAndReferenceIdForUpdate(
                PaymentProvider.STRIPE, "pi_timeout_123"
        )).thenReturn(Optional.empty());
        when(paymentRepository.findByIdAndProviderForUpdate(
                payment.getId(), PaymentProvider.STRIPE
        )).thenReturn(Optional.of(payment));

        service.processCallback(new ProcessPaymentCallbackCommand(
                PaymentProvider.STRIPE,
                payment.getId(),
                "pi_timeout_123",
                ProcessPaymentCallbackCommand.CallbackStatus.SUCCESS,
                null
        ));

        assertThat(payment.getReferenceId()).isEqualTo("pi_timeout_123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).saveStateAndOutbox(
                same(payment),
                argThat(events -> events.size() == 1 && events.getFirst() instanceof PaymentSucceeded)
        );
    }

    private Payment paymentWithStatus(PaymentStatus status) {
        return new Payment(
                PaymentId.generate(),
                AccountId.generate(),
                AccountId.generate(),
                "pi_test_123",
                new Money(new BigDecimal("25.00"), Currency.getInstance("TRY")),
                PaymentProvider.STRIPE,
                status
        );
    }
}
