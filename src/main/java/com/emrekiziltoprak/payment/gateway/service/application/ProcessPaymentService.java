package com.emrekiziltoprak.payment.gateway.service.application;

import com.emrekiziltoprak.payment.gateway.service.domain.GatewayStatus;
import com.emrekiziltoprak.payment.gateway.service.domain.IdempotencyRecord;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.GatewayTimeoutException;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentUseCase;
import com.emrekiziltoprak.payment.gateway.service.ports.out.*;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

public class ProcessPaymentService implements ProcessPaymentUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final IdempotencyRepository idempotencyRepository;
    private final OutboxRepository outboxRepository;


    public ProcessPaymentService(PaymentRepository paymentRepository, PaymentGatewayPort paymentGatewayPort, IdempotencyRepository idempotencyRepository, OutboxRepository outboxRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentGatewayPort = paymentGatewayPort;
        this.idempotencyRepository = idempotencyRepository;
        this.outboxRepository = outboxRepository;
    }

    @Override
    public PaymentId processPayment(ProcessPaymentCommand command) {

        Optional<PaymentId> paymentId = idempotencyRepository.findPaymentIdByKey(command.idempotencyKey());
        if (paymentId.isPresent()) return paymentId.get();

        else {
            PaymentId paymentId1 = PaymentId.generate();
            Payment paymentToSave = new Payment(paymentId1, command.sourceAccountId(), command.destinationAccountId(), command.amount());

            paymentRepository.saveInitiatedPaymentWithIdempotencyKey(paymentToSave, new IdempotencyRecord(
                    command.idempotencyKey(),
                    paymentId1,
                    Instant.now()
            ));

            try {

                PaymentGatewayResult result = paymentGatewayPort.processPayment(paymentToSave);

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



}
