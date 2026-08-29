package com.emrekiziltoprak.payment.gateway.service.application;

import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentFailed;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentSucceeded;
import com.emrekiziltoprak.payment.gateway.service.ports.out.IdempotencyRepository;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentCallbackCommandTestFixture.aCallback;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentCallbackCommandTestFixture.aFailedCallback;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentCallbackCommandTestFixture.aSuccessfulCallback;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.DEFAULT_PROVIDER;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.DEFAULT_PROVIDER_REFERENCE;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPayment;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPaymentWithStatus;
import static com.emrekiziltoprak.payment.gateway.service.testsupport.PaymentTestFixture.aPendingPayment;
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
        Payment payment = aPendingPayment();
        when(paymentRepository.findByProviderAndReferenceIdForUpdate(
                DEFAULT_PROVIDER, DEFAULT_PROVIDER_REFERENCE
        )).thenReturn(Optional.of(payment));

        service.processCallback(aSuccessfulCallback());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).saveStateAndOutbox(
                same(payment),
                argThat(events -> events.size() == 1 && events.getFirst() instanceof PaymentSucceeded)
        );
    }

    @Test
    void failsPendingPaymentAndSavesOutboxEvent() {
        Payment payment = aPendingPayment();
        when(paymentRepository.findByProviderAndReferenceIdForUpdate(
                DEFAULT_PROVIDER, DEFAULT_PROVIDER_REFERENCE
        )).thenReturn(Optional.of(payment));

        service.processCallback(aFailedCallback("declined"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailure().code()).isEqualTo(PaymentFailureCode.DECLINED);
        assertThat(payment.getFailure().detail()).isEqualTo("declined");
        verify(paymentRepository).saveStateAndOutbox(
                same(payment),
                argThat(events -> events.size() == 1
                        && events.getFirst() instanceof PaymentFailed failed
                        && failed.reason().equals("declined"))
        );
    }

    @Test
    void ignoresDuplicateTerminalCallbackWithoutCreatingAnotherOutboxEvent() {
        Payment payment = aPaymentWithStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByProviderAndReferenceIdForUpdate(
                DEFAULT_PROVIDER, DEFAULT_PROVIDER_REFERENCE
        )).thenReturn(Optional.of(payment));

        service.processCallback(aSuccessfulCallback());

        verify(paymentRepository, never()).saveStateAndOutbox(
                same(payment),
                argThat(events -> true)
        );
    }

    @Test
    void rejectsCallbackForMismatchedTerminalStatePayment() {
        // Payment already SUCCEEDED, but callback says FAILED
        Payment payment = aPaymentWithStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByProviderAndReferenceIdForUpdate(
                DEFAULT_PROVIDER, DEFAULT_PROVIDER_REFERENCE
        )).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.processCallback(aFailedCallback(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal state");

        verify(paymentRepository, never()).saveStateAndOutbox(
                same(payment),
                argThat(events -> true)
        );
    }

    @Test
    void resolvesTimedOutPaymentByInternalIdAndStoresGatewayReference() {
        String callbackReference = "pi_timeout_123";
        Payment payment = aPayment()
                .withoutProviderReference()
                .buildRestored();

        when(paymentRepository.findByProviderAndReferenceIdForUpdate(
                DEFAULT_PROVIDER, callbackReference
        )).thenReturn(Optional.empty());
        when(paymentRepository.findByIdAndProviderForUpdate(
                payment.getId(), DEFAULT_PROVIDER
        )).thenReturn(Optional.of(payment));

        service.processCallback(aCallback()
                .withPaymentId(payment.getId())
                .withProviderReference(callbackReference)
                .successful()
                .build());

        assertThat(payment.getReferenceId()).isEqualTo(callbackReference);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).saveStateAndOutbox(
                same(payment),
                argThat(events -> events.size() == 1 && events.getFirst() instanceof PaymentSucceeded)
        );
    }
}
