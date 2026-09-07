package com.emrekiziltoprak.payment.gateway.service.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.emrekiziltoprak.payment.gateway.service.domain.IdempotencyRecord;
import com.emrekiziltoprak.payment.gateway.service.domain.Payment;
import com.emrekiziltoprak.payment.gateway.service.domain.PaymentId;
import com.emrekiziltoprak.payment.gateway.service.domain.ProviderPaymentReference;
import com.emrekiziltoprak.payment.gateway.service.domain.TransitionResult;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.GatewayTimeoutException;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.PaymentNotFoundException;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.LifecycleObservationContext;
import com.emrekiziltoprak.payment.gateway.service.domain.lifecycle.PaymentLifecycleObservation;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCallbackUseCase;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentCommand;
import com.emrekiziltoprak.payment.gateway.service.ports.in.ProcessPaymentUseCase;
import com.emrekiziltoprak.payment.gateway.service.ports.out.IdempotencyRepository;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentGatewayPort;
import com.emrekiziltoprak.payment.gateway.service.ports.out.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProcessPaymentService implements ProcessPaymentUseCase, ProcessPaymentCallbackUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final IdempotencyRepository idempotencyRepository;

    public ProcessPaymentService(
            PaymentRepository paymentRepository,
            PaymentGatewayPort paymentGatewayPort,
            IdempotencyRepository idempotencyRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentGatewayPort = paymentGatewayPort;
        this.idempotencyRepository = idempotencyRepository;
    }

    @Override
    public PaymentId processPayment(ProcessPaymentCommand command) {
        Optional<PaymentId> existingPaymentId = idempotencyRepository
                .findPaymentIdByKey(command.idempotencyKey());
        if (existingPaymentId.isPresent()) {
            return existingPaymentId.get();
        }

        PaymentId paymentId = PaymentId.generate();
        Payment payment = Payment.initiate(
                paymentId,
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.amount(),
                command.paymentProvider()
        );

        paymentRepository.saveInitiatedPaymentWithIdempotencyKey(
                payment,
                new IdempotencyRecord(command.idempotencyKey(), paymentId, Instant.now())
        );

        try {
            PaymentLifecycleObservation observation = Objects.requireNonNull(
                    paymentGatewayPort.processPayment(payment),
                    "payment gateway observation cannot be null"
            );
            handleTransitionResult(payment, payment.observe(observation));
        } catch (GatewayTimeoutException exception) {
            payment.markAsProcessing("Gateway timeout: " + exception.getMessage());
            paymentRepository.saveStateAndOutbox(payment, payment.getDomainEvents());
            payment.clearDomainEvents();
        }

        return paymentId;
    }

    @Override
    public void processCallback(PaymentLifecycleObservation observation) {
        Payment payment = findObservedPayment(observation);
        handleTransitionResult(payment, payment.observe(observation));
    }

    private Payment findObservedPayment(PaymentLifecycleObservation observation) {
        Objects.requireNonNull(observation, "observation cannot be null");

        LifecycleObservationContext context = observation.context();
        ProviderPaymentReference reference = context.providerReference();

        Optional<Payment> paymentByReference = reference.hasValue()
                ? paymentRepository.findByProviderAndReferenceIdForUpdate(
                        reference.provider(),
                        reference.value()
                )
                : Optional.empty();

        if (paymentByReference.isEmpty()) {
            PaymentId internalPaymentId = context.internalPaymentId()
                    .orElseThrow(() -> paymentNotFound(context));

            return paymentRepository
                    .findByIdAndProviderForUpdate(internalPaymentId, reference.provider())
                    .orElseThrow(() -> paymentNotFound(context));
        }

        Payment payment = paymentByReference.get();
        context.internalPaymentId()
                .filter(internalPaymentId -> !payment.getId().equals(internalPaymentId))
                .ifPresent(internalPaymentId -> {
                    throw new IllegalStateException(
                            "Payment ID mismatch for provider reference: " + reference.value()
                    );
                });

        return payment;
    }

    private PaymentNotFoundException paymentNotFound(LifecycleObservationContext context) {
        ProviderPaymentReference reference = context.providerReference();
        return new PaymentNotFoundException(
                "Payment not found for provider " + reference.provider()
                        + " with reference: "
                        + (reference.hasValue() ? reference.value() : "<unavailable>")
        );
    }

    private void handleTransitionResult(Payment payment, TransitionResult result) {
        switch (result) {
            case TransitionResult.Applied ignored -> {
                paymentRepository.saveStateAndOutbox(payment, payment.getDomainEvents());
                payment.clearDomainEvents();
            }
            case TransitionResult.Idempotent ignored ->
                    log.debug(
                            "Duplicate payment lifecycle observation ignored. paymentId={}",
                            payment.getId()
                    );
            case TransitionResult.Stale stale ->
                    log.info(
                            "Stale payment lifecycle observation ignored. paymentId={}, reason={}",
                            payment.getId(),
                            stale.reason()
                    );
            case TransitionResult.Conflict conflict -> {
                log.error(
                        "Payment lifecycle observation conflict. paymentId={}, details={}",
                        payment.getId(),
                        conflict.details()
                );
                throw new IllegalStateException(
                        "Payment lifecycle observation conflict: " + conflict.details()
                );
            }
        }
    }
}
