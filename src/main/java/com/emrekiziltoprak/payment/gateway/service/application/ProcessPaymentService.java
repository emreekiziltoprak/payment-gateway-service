package com.emrekiziltoprak.payment.gateway.service.application;

import java.time.Instant;
import java.util.Optional;

import com.emrekiziltoprak.payment.gateway.service.domain.IdempotencyRecord;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentCancellationReason;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailure;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentFailureCode;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import com.emrekiziltoprak.payment.gateway.service.domain.ProviderPaymentReference;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.GatewayTimeoutException;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.PaymentNotFoundException;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackUseCase;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentUseCase;
import com.emrekiziltoprak.payment.gateway.service.ports.out.IdempotencyRepository;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayResult;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentRepository;


public class ProcessPaymentService implements ProcessPaymentUseCase,
        ProcessPaymentCallbackUseCase
{
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final IdempotencyRepository idempotencyRepository;

    public ProcessPaymentService(PaymentRepository paymentRepository, PaymentGatewayPort paymentGatewayPort, IdempotencyRepository idempotencyRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentGatewayPort = paymentGatewayPort;
        this.idempotencyRepository = idempotencyRepository;
    }

    @Override
    public PaymentId processPayment(ProcessPaymentCommand command) {

        Optional<PaymentId> paymentId = idempotencyRepository.findPaymentIdByKey(command.idempotencyKey());
        if (paymentId.isPresent()) return paymentId.get();

        else {
            PaymentId paymentId1 = PaymentId.generate();
            Payment paymentToSave = Payment.initiate(
                    paymentId1,
                    command.sourceAccountId(),
                    command.destinationAccountId(),
                    command.amount(),
                    command.paymentProvider()
            );

            paymentRepository.saveInitiatedPaymentWithIdempotencyKey(paymentToSave, new IdempotencyRecord(
                    command.idempotencyKey(),
                    paymentId1,
                    Instant.now()
            ));

            try {

                PaymentGatewayResult result = paymentGatewayPort.processPayment(paymentToSave);

                if (result.transactionId() != null) {
                    ProviderPaymentReference newRef = new ProviderPaymentReference(
                            paymentToSave.getPaymentRef().provider(),
                            result.transactionId()
                    );

                    paymentToSave.assignProviderReference(newRef);
                }


                switch (result.status()) {
                    case CAPTURED ->
                            paymentToSave.markAsCaptured();

                    case AUTHORIZED ->
                            paymentToSave.markAsAuthorized();

                    case PROCESSING ->
                            paymentToSave.markAsProcessing(result.failureReason());

                    case REQUIRES_ACTION ->
                            paymentToSave.markAsRequiredAction(result.failureReason());

                    case FAILED ->
                            paymentToSave.markAsFailed(
                                    PaymentFailure.of(
                                            PaymentFailureCode.DECLINED,
                                            result.failureReason()
                                    )
                            );

                    case CANCELLED ->
                            paymentToSave.markAsCancelled(
                                    PaymentCancellationReason.PROVIDER_CANCELLED
                            );

                    case ERROR ->
                            paymentToSave.markAsFailed(
                                    PaymentFailure.of(
                                            PaymentFailureCode.GATEWAY_ERROR,
                                            result.failureReason()
                                    )
                            );
                }
            }
            catch (GatewayTimeoutException e) {
                paymentToSave.markAsProcessing("Gateway timeout: " + e.getMessage());
                paymentRepository.saveStateAndOutbox(paymentToSave, paymentToSave.getDomainEvents());
                return paymentId1;
            }
            catch (Exception e) {
                paymentToSave.markAsFailed(
                        PaymentFailure.of(PaymentFailureCode.GATEWAY_ERROR, e.getMessage())
                );
                paymentRepository.saveStateAndOutbox(paymentToSave, paymentToSave.getDomainEvents());
                return paymentId1;
            }
            paymentRepository.saveStateAndOutbox(paymentToSave, paymentToSave.getDomainEvents());
            return paymentId1;
        }
    }


    //update payment item with the information coming from webhook controller via ProcessPaymentCallbackCommand
    //webhook controller(StripeWebHookController etc.) calls this method from service instance
    //webhook (success) -> db  item -> (success)
    @Override
    public void processCallback(ProcessPaymentCallbackCommand command) {
        Payment relatedPayment = findCallbackPayment(command);

        if (isDuplicateTerminalCallback(relatedPayment, command)) {
            return;
        }

        if (relatedPayment.getStatus() != PaymentStatus.PROCESSING
        && relatedPayment.getStatus() != PaymentStatus.REQUIRES_ACTION
        && relatedPayment.getStatus() != PaymentStatus.INITIATED
        && relatedPayment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Payment is already in a terminal state and cannot be updated by callback. Current status: "
                            + relatedPayment.getStatus()
            );
        }

        switch (command.status()) {
            case CAPTURABLE -> relatedPayment.markAsAuthorized();
            case CAPTURED -> relatedPayment.markAsCaptured();
            case FAILED -> relatedPayment.markAsFailed(command.failure());
            case REQUIRES_ACTION -> relatedPayment.markAsRequiredAction(command.failureReason());
            case PROCESSING -> relatedPayment.markAsProcessing(command.failureReason());
            case CANCELED -> relatedPayment.markAsFailed(
                PaymentFailure.of(
                    PaymentFailureCode.CANCELLED,
                    "Canceled by customer"
                )
            );
        }

        if (relatedPayment.getDomainEvents().isEmpty()) {
            return;
        }

        paymentRepository.saveStateAndOutbox(relatedPayment, relatedPayment.getDomainEvents());
    }

    private Payment findCallbackPayment(ProcessPaymentCallbackCommand command) {
        Optional<Payment> paymentByReference = paymentRepository
                .findByProviderAndReferenceIdForUpdate(
                        command.provider(),
                        command.paymentReference()
                );

        if (paymentByReference.isEmpty()) {
            if (command.paymentId() == null) {
                throw paymentNotFound(command);
            }

            Payment payment = paymentRepository
                    .findByIdAndProviderForUpdate(command.paymentId(), command.provider())
                    .orElseThrow(() -> paymentNotFound(command));
            payment.assignProviderReference(new ProviderPaymentReference(
                    command.provider(),
                    command.paymentReference()
            ));
            return payment;
        }

        Payment payment = paymentByReference.get();

        if (command.paymentId() != null
                && !payment.getId().equals(command.paymentId())) {
            throw new IllegalStateException(
                    "Payment ID mismatch for provider reference: "
                            + command.paymentReference()
            );
        }

        return payment;
    }

    private PaymentNotFoundException paymentNotFound(ProcessPaymentCallbackCommand command) {
        return new PaymentNotFoundException(
                "Payment not found for provider " + command.provider()
                        + " with reference: " + command.paymentReference()
        );
    }

    private boolean isDuplicateTerminalCallback(
            Payment payment,
            ProcessPaymentCallbackCommand command
    ) {
        if (command.status() == ProcessPaymentCallbackCommand.CallbackStatus.CAPTURED) {
            return payment.getStatus() == PaymentStatus.CAPTURED;
        }

        if (command.status() == ProcessPaymentCallbackCommand.CallbackStatus.FAILED
                || command.status() == ProcessPaymentCallbackCommand.CallbackStatus.CANCELED) {
            return payment.getStatus() == PaymentStatus.FAILED;
        }

        return false;
    }
}
