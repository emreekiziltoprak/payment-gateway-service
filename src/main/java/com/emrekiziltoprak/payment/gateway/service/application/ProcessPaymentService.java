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
            Payment paymentToSave = new Payment(paymentId1, command.sourceAccountId(), command.destinationAccountId(), null, command.amount(), command.paymentProvider());

            paymentRepository.saveInitiatedPaymentWithIdempotencyKey(paymentToSave, new IdempotencyRecord(
                    command.idempotencyKey(),
                    paymentId1,
                    Instant.now()
            ));

            try {

                PaymentGatewayResult result = paymentGatewayPort.processPayment(paymentToSave);

                if(result.transactionId() != null) {
                 paymentToSave.setReferenceId(result.transactionId());
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
                    "Only non-terminal payments can be updated by callback. Current status: "
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
        Payment payment = paymentRepository
                .findByProviderAndReferenceIdForUpdate(
                        command.provider(),
                        command.paymentReference()
                )
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found for provider " + command.provider()
                                + " with reference: " + command.paymentReference()
                ));

        if (command.paymentId() != null
                && !payment.getId().equals(command.paymentId())) {
            throw new IllegalStateException(
                    "Payment ID mismatch for provider reference: "
                            + command.paymentReference()
            );
        }

        return payment;
    }

    private boolean assignGatewayReferenceIfMissing(Payment payment, String callbackReference) {
        if (payment.getReferenceId() == null) {
            payment.setReferenceId(callbackReference);
            return true;
        }

        if (!payment.getReferenceId().equals(callbackReference)) {
            throw new IllegalStateException(
                    "Callback reference does not match the stored payment reference"
            );
        }

        return false;
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
