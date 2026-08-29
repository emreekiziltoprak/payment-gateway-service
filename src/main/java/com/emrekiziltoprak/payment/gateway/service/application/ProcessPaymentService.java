package com.emrekiziltoprak.payment.gateway.service.application;

import com.emrekiziltoprak.payment.gateway.service.domain.IdempotencyRecord;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentStatus;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.GatewayTimeoutException;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.PaymentNotFoundException;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackUseCase;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentUseCase;
import com.emrekiziltoprak.payment.gateway.service.ports.out.*;

import java.time.Instant;
import java.util.Optional;

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

                if(result.transactionId() != null) {
                    paymentToSave.assignProviderReference(result.transactionId());
                }


                switch (result.status()) {
                    case CAPTURED -> paymentToSave.markAsSucceeded();

                    case AUTHORIZED -> paymentToSave.markAsAuthorized();

                    case DECLINED -> paymentToSave.markAsFailed("Bank rejection: " + result.failureReason());

                    case ERROR -> paymentToSave.markAsFailed("Gateway error: " + result.failureReason());

                    case PENDING -> paymentToSave.markAsPending("Pending: " + result.failureReason());
                }
            }
            catch (GatewayTimeoutException e) {
                paymentToSave.markAsPending("Gateway timeout: " + e.getMessage());
                paymentRepository.saveStateAndOutbox(paymentToSave, paymentToSave.getDomainEvents());
                return paymentId1;
            }
            catch (Exception e) {
                paymentToSave.markAsFailed("Error: " + e.getMessage());
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

        if (relatedPayment.getStatus() != PaymentStatus.PENDING
        && relatedPayment.getStatus() != PaymentStatus.INITIATED
        && relatedPayment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Payment is already in a terminal state and cannot be updated by callback. Current status: "
                            + relatedPayment.getStatus()
            );
        }

        switch (command.status()) {
            case SUCCESS -> relatedPayment.markAsSucceeded();
            case FAILED -> relatedPayment.markAsFailed(
                    command.failureReason() != null ? command.failureReason() : "Payment failed"
            );
            case REQUIRES_ACTION, PROCESSING -> relatedPayment.markAsPending(command.failureReason());
            case CANCELED -> relatedPayment.markAsFailed("Canceled by customer");
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
            payment.assignProviderReference(command.paymentReference());
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
        if (command.status() == ProcessPaymentCallbackCommand.CallbackStatus.SUCCESS) {
            return payment.getStatus() == PaymentStatus.SUCCEEDED;
        }

        if (command.status() == ProcessPaymentCallbackCommand.CallbackStatus.FAILED
                || command.status() == ProcessPaymentCallbackCommand.CallbackStatus.CANCELED) {
            return payment.getStatus() == PaymentStatus.FAILED;
        }

        return false;
    }
}
