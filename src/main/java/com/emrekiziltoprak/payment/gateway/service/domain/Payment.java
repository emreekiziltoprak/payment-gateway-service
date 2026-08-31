package com.emrekiziltoprak.payment.gateway.service.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentCancelled;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentCaptured;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentEvent;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentFailed;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentInitiated;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentProcessing;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentRefunded;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentRequiresAction;
import com.emrekiziltoprak.payment.gateway.service.domain.exception.ProviderReferenceConflictException;

import lombok.Getter;

@Getter
public class Payment {

    private final PaymentId id;
    private final AccountId sourceAccountId;
    private final AccountId destinationAccountId;
    private final Money amount;

    private ProviderPaymentReference paymentRef;
    private final Instant createdAt;
    private final List<PaymentEvent> domainEvents = new ArrayList<>();

    private PaymentStatus status;
    private PaymentFailure failure;
    private PaymentCancellationReason cancellationReason;

    private Instant updatedAt;

    private Payment(PaymentId id,
                    AccountId sourceAccountId,
                    AccountId destinationAccountId,
                    String referenceId,
                    Money amount,
                    PaymentProvider paymentProvider,
                    PaymentStatus status,
                    Instant createdAt,
                    Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId cannot be null");
        this.destinationAccountId = Objects.requireNonNull(destinationAccountId, "destinationAccountId cannot be null");
        this.amount = Objects.requireNonNull(amount, "amount cannot be null");
        this.paymentRef = new ProviderPaymentReference(paymentProvider, referenceId);
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public static Payment initiate(PaymentId id,
                                   AccountId sourceAccountId,
                                   AccountId destinationAccountId,
                                   Money amount,
                                   PaymentProvider paymentProvider) {
        Instant initiatedAt = Instant.now();
        Payment payment = new Payment(
                id,
                sourceAccountId,
                destinationAccountId,
                null,
                amount,
                paymentProvider,
                PaymentStatus.INITIATED,
                initiatedAt,
                initiatedAt
        );

        payment.addDomainEvent(new PaymentInitiated(
                id,
                sourceAccountId,
                destinationAccountId,
                amount,
                initiatedAt
        ));
        return payment;
    }

    public static Payment restore(PaymentId id,
                                  AccountId sourceAccountId,
                                  AccountId destinationAccountId,
                                  String referenceId,
                                  Money amount,
                                  PaymentProvider paymentProvider,
                                  PaymentStatus status,
                                  Instant createdAt,
                                  Instant updatedAt) {
        PaymentFailure restoredFailure = status == PaymentStatus.FAILED
                ? PaymentFailure.of(PaymentFailureCode.GATEWAY_ERROR, "Failure detail unavailable")
                : null;
        return restore(id, sourceAccountId, destinationAccountId, referenceId, amount,
                paymentProvider, status, restoredFailure, null, createdAt, updatedAt);
    }

    public static Payment restore(PaymentId id,
                                  AccountId sourceAccountId,
                                  AccountId destinationAccountId,
                                  String referenceId,
                                  Money amount,
                                  PaymentProvider paymentProvider,
                                  PaymentStatus status,
                                  PaymentFailure failure,
                                  Instant createdAt,
                                  Instant updatedAt) {
        return restore(id, sourceAccountId, destinationAccountId, referenceId, amount,
                paymentProvider, status, failure, null, createdAt, updatedAt);
    }

    public static Payment restore(PaymentId id,
                                  AccountId sourceAccountId,
                                  AccountId destinationAccountId,
                                  String referenceId,
                                  Money amount,
                                  PaymentProvider paymentProvider,
                                  PaymentStatus status,
                                  PaymentFailure failure,
                                  PaymentCancellationReason cancellationReason,
                                  Instant createdAt,
                                  Instant updatedAt) {
        if (status == PaymentStatus.FAILED && failure == null) {
            throw new IllegalArgumentException("Failed payment must have failure information");
        }
        if (status != PaymentStatus.CANCELLED && cancellationReason != null) {
            throw new IllegalArgumentException(
                    "Cancellation reason is only valid for cancelled payments"
            );
        }
        Payment payment = new Payment(
                id,
                sourceAccountId,
                destinationAccountId,
                referenceId,
                amount,
                paymentProvider,
                status,
                createdAt,
                updatedAt
        );
        payment.failure = failure;
        payment.cancellationReason = cancellationReason;
        return payment;
    }

    public void assignProviderReference(ProviderPaymentReference newPaymentRef) {
        Objects.requireNonNull(newPaymentRef, "providerReference cant be null");

        if (!paymentRef.provider().equals(newPaymentRef.provider())) {
            throw new ProviderReferenceConflictException("Payment provider cannot be changed");
        }

        if (paymentRef.hasValue() && !paymentRef.equals(newPaymentRef)) {
            throw new ProviderReferenceConflictException("Provider reference cannot be replaced");
        }

        if (!paymentRef.hasValue()) {
            this.paymentRef = newPaymentRef;
            this.updatedAt = Instant.now();
        }
    }

    public void assignProviderReference(String referenceId) {
        assignProviderReference(new ProviderPaymentReference(paymentRef.provider(), referenceId));
    }

    public String getReferenceId() {
        return paymentRef.value();
    }

    public PaymentProvider getPaymentProvider() {
        return paymentRef.provider();
    }

    public void markAsCaptured() {
        if (status == PaymentStatus.CAPTURED) {
            return;
        }
        if (status != PaymentStatus.INITIATED
                && status != PaymentStatus.PROCESSING
                && status != PaymentStatus.REQUIRES_ACTION
                && status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment can only be marked as captured from INITIATED or PROCESSING or AUTHORIZED status");
        }
        status = PaymentStatus.CAPTURED;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentCaptured(id, updatedAt));
    }

    public void markAsAuthorized() {
        if (status != PaymentStatus.INITIATED
                && status != PaymentStatus.PROCESSING
                && status != PaymentStatus.REQUIRES_ACTION) {
            throw new IllegalStateException("Payment can only be marked as authorized from INITIATED, PROCESSING or REQUIRES_ACTION status");
        }
        status = PaymentStatus.AUTHORIZED;
        updatedAt = Instant.now();
    }

    public void markAsProcessing(String reason) {
        if (status == PaymentStatus.PROCESSING) {
            return;
        }
        if (status != PaymentStatus.INITIATED
            && status != PaymentStatus.REQUIRES_ACTION
        ) {
            throw new IllegalStateException("Payment can only be marked as pending from INITIATED status");
        }
        status = PaymentStatus.PROCESSING;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentProcessing(id, reason, updatedAt));
    }

    public void markAsRequiredAction(String reason) {
        if (status == PaymentStatus.REQUIRES_ACTION) {
            return;
        }

        if (status != PaymentStatus.INITIATED
                && status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Payment can only require action from INITIATED or PROCESSING status"
            );
        }
        status = PaymentStatus.REQUIRES_ACTION;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentRequiresAction(id, reason, updatedAt));
    }

    public void markAsFailed(PaymentFailure failure) {
        if (status == PaymentStatus.FAILED) {
            return;
        }
        if (status != PaymentStatus.INITIATED
                && status != PaymentStatus.PROCESSING
                && status != PaymentStatus.REQUIRES_ACTION
                && status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment can only be marked as failed from INITIATED or PENDING status");
        }
        this.failure = Objects.requireNonNull(failure, "failure cannot be null");
        status = PaymentStatus.FAILED;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentFailed(id, failure, updatedAt));
    }

    public void markAsFailed(String reason) {
        markAsFailed(PaymentFailure.of(PaymentFailureCode.DECLINED, reason));
    }

    public void markAsCancelled(PaymentCancellationReason reason) {
        if (status == PaymentStatus.CANCELLED) {
            return;
        }

        if (status != PaymentStatus.INITIATED
                && status != PaymentStatus.PROCESSING
                && status != PaymentStatus.REQUIRES_ACTION
                && status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Payment cannot be cancelled from status: " + status
            );
        }
        cancellationReason = Objects.requireNonNull(reason, "cancellationReason cannot be null");
        status = PaymentStatus.CANCELLED;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentCancelled(id, cancellationReason, updatedAt));
    }

    public void refund() {
        if (status != PaymentStatus.CAPTURED
                && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Only captured or partially refunded payments can be refunded");
        }
        status = PaymentStatus.REFUNDED;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentRefunded(id, updatedAt));
    }

    public List<PaymentEvent> getDomainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    private void addDomainEvent(PaymentEvent event) {
        domainEvents.add(event);
    }

}
