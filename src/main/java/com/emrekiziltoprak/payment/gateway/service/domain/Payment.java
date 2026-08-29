package com.emrekiziltoprak.payment.gateway.service.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentEvent;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentFailed;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentInitiated;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentPending;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentRefunded;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentSucceeded;
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
                paymentProvider, status, restoredFailure, createdAt, updatedAt);
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
        if (status == PaymentStatus.FAILED && failure == null) {
            throw new IllegalArgumentException("Failed payment must have failure information");
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

    public void markAsSucceeded() {
        if (status == PaymentStatus.SUCCEEDED) {
            return;
        }
        if (status != PaymentStatus.INITIATED
                && status != PaymentStatus.PENDING
                && status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment can only be marked as succeeded from INITIATED or PENDING status");
        }
        status = PaymentStatus.SUCCEEDED;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentSucceeded(id, updatedAt));
    }

    public void markAsAuthorized() {
        if (status != PaymentStatus.INITIATED) {
            throw new IllegalStateException("Payment can only be marked as authorized from INITIATED status");
        }
        status = PaymentStatus.AUTHORIZED;
        updatedAt = Instant.now();
    }

    public void markAsPending(String reason) {
        if (status == PaymentStatus.PENDING) {
            return;
        }
        if (status != PaymentStatus.INITIATED && status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment can only be marked as pending from INITIATED or AUTHORIZED status");
        }
        status = PaymentStatus.PENDING;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentPending(id, reason, updatedAt));
    }

    public void markAsFailed(PaymentFailure failure) {
        if (status == PaymentStatus.FAILED) {
            return;
        }
        if (status != PaymentStatus.INITIATED
                && status != PaymentStatus.PENDING
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

    public void refund() {
        if (status != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Only succeeded payments can be refunded");
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
