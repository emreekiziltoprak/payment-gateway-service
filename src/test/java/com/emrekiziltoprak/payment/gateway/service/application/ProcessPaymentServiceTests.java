package com.emrekiziltoprak.payment.gateway.service.application;

import com.emrekiziltoprak.payment.gateway.service.domain.GatewayStatus;
import com.emrekiziltoprak.payment.gateway.service.domain.IdempotencyRecord;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentEvent;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentInitiated;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentSucceeded;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.out.IdempotencyRepository;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayResult;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static com.emrekiziltoprak.payment.gateway.service.testsupport.ProcessPaymentCommandTestFixture.aProcessPaymentCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcessPaymentServiceTests {

    private static final String PROVIDER_REFERENCE = "gateway-transaction-123";

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentGatewayPort paymentGatewayPort = mock(PaymentGatewayPort.class);
    private final IdempotencyRepository idempotencyRepository = mock(IdempotencyRepository.class);
    private final ProcessPaymentService service = new ProcessPaymentService(
            paymentRepository,
            paymentGatewayPort,
            idempotencyRepository
    );

    @Test
    void initiatesNewPaymentBeforeGatewayCallAndPersistsSuccessfulOutcome() {
        ProcessPaymentCommand command = aProcessPaymentCommand();
        when(idempotencyRepository.findPaymentIdByKey(command.idempotencyKey()))
                .thenReturn(Optional.empty());
        when(paymentGatewayPort.processPayment(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment initiatedPayment = invocation.getArgument(0);
                    assertInitiatedFrom(initiatedPayment, command);
                    return new PaymentGatewayResult(
                            GatewayStatus.CAPTURED,
                            PROVIDER_REFERENCE,
                            null
                    );
                });

        PaymentId paymentId = service.processPayment(command);

        ArgumentCaptor<Payment> initiatedPaymentCaptor = ArgumentCaptor.forClass(Payment.class);
        ArgumentCaptor<IdempotencyRecord> idempotencyRecordCaptor =
                ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(paymentRepository).saveInitiatedPaymentWithIdempotencyKey(
                initiatedPaymentCaptor.capture(),
                idempotencyRecordCaptor.capture()
        );

        Payment payment = initiatedPaymentCaptor.getValue();
        IdempotencyRecord idempotencyRecord = idempotencyRecordCaptor.getValue();

        assertThat(paymentId).isEqualTo(payment.getId());
        assertThat(idempotencyRecord.key()).isEqualTo(command.idempotencyKey());
        assertThat(idempotencyRecord.paymentId()).isEqualTo(paymentId);
        assertThat(idempotencyRecord.createdAt()).isNotNull();
        assertThat(payment.getReferenceId()).isEqualTo(PROVIDER_REFERENCE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        ArgumentCaptor<List<PaymentEvent>> eventsCaptor = ArgumentCaptor.captor();
        verify(paymentRepository).saveStateAndOutbox(
                same(payment),
                eventsCaptor.capture()
        );
        assertThat(eventsCaptor.getValue())
                .extracting(Object::getClass)
                .containsExactly(PaymentInitiated.class, PaymentSucceeded.class);

        InOrder callOrder = inOrder(paymentRepository, paymentGatewayPort);
        callOrder.verify(paymentRepository).saveInitiatedPaymentWithIdempotencyKey(
                same(payment),
                same(idempotencyRecord)
        );
        callOrder.verify(paymentGatewayPort).processPayment(same(payment));
        callOrder.verify(paymentRepository).saveStateAndOutbox(
                same(payment),
                same(eventsCaptor.getValue())
        );
    }

    @Test
    void returnsExistingPaymentIdWithoutStartingAnotherPayment() {
        ProcessPaymentCommand command = aProcessPaymentCommand();
        PaymentId existingPaymentId = PaymentId.generate();
        when(idempotencyRepository.findPaymentIdByKey(command.idempotencyKey()))
                .thenReturn(Optional.of(existingPaymentId));

        PaymentId paymentId = service.processPayment(command);

        assertThat(paymentId).isEqualTo(existingPaymentId);
        verify(idempotencyRepository).findPaymentIdByKey(command.idempotencyKey());
        verifyNoInteractions(paymentRepository, paymentGatewayPort);
    }

    @Test
    void mapsTechnicalGatewayFailureToStableDomainCode() {
        ProcessPaymentCommand command = aProcessPaymentCommand();
        when(idempotencyRepository.findPaymentIdByKey(command.idempotencyKey()))
                .thenReturn(Optional.empty());
        when(paymentGatewayPort.processPayment(any(Payment.class)))
                .thenReturn(new PaymentGatewayResult(
                        GatewayStatus.ERROR,
                        null,
                        "Provider is temporarily unavailable"
                ));

        service.processPayment(command);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveStateAndOutbox(paymentCaptor.capture(), any());

        Payment payment = paymentCaptor.getValue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailure().code()).isEqualTo(PaymentFailureCode.GATEWAY_ERROR);
        assertThat(payment.getFailure().detail()).isEqualTo("Provider is temporarily unavailable");
    }

    private void assertInitiatedFrom(Payment payment, ProcessPaymentCommand command) {
        assertThat(payment)
                .extracting(
                        Payment::getSourceAccountId,
                        Payment::getDestinationAccountId,
                        Payment::getAmount,
                        Payment::getPaymentProvider,
                        Payment::getStatus,
                        Payment::getReferenceId
                )
                .containsExactly(
                        command.sourceAccountId(),
                        command.destinationAccountId(),
                        command.amount(),
                        command.paymentProvider(),
                        PaymentStatus.INITIATED,
                        null
                );
        assertThat(payment.getCreatedAt()).isEqualTo(payment.getUpdatedAt());
        assertThat(payment.getDomainEvents())
                .singleElement()
                .isInstanceOfSatisfying(PaymentInitiated.class, event -> {
                    assertThat(event.paymentId()).isEqualTo(payment.getId());
                    assertThat(event.sourceAccountId()).isEqualTo(command.sourceAccountId());
                    assertThat(event.destinationAccountId()).isEqualTo(command.destinationAccountId());
                    assertThat(event.amount()).isEqualTo(command.amount());
                    assertThat(event.occurredAt()).isEqualTo(payment.getCreatedAt());
                });
    }
}
