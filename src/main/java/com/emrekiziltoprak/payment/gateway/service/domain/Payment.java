package com.emrekiziltoprak.payment.gateway.service.domain;

import com.emrekiziltoprak.payment.gateway.service.domain.event.*;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public class Payment {

    private final PaymentId id;
    private final AccountId sourceAccountId;
    private final AccountId destinationAccountId;
    private String referenceId;
    private final Money amount;
    private final PaymentProvider paymentProvider;
    private final Instant createdAt;
    private final List<PaymentEvent> domainEvents = new ArrayList<>();

    private PaymentStatus status;
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
        this.paymentProvider = paymentProvider;
        this.referenceId = referenceId;
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
        return new Payment(
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
    }

    public void assignProviderReference(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("referenceId cannot be blank");
        }
        if (this.referenceId != null && !this.referenceId.equals(referenceId)) {
            throw new IllegalStateException("Provider reference cannot be replaced");
        }
        if (this.referenceId == null) {
            this.referenceId = referenceId;
            this.updatedAt = Instant.now();
        }
    }

    public void markAsSucceeded() {
        if (status == PaymentStatus.SUCCEEDED) {
            return;
        }
        if (status != PaymentStatus.INITIATED && status != PaymentStatus.PENDING) {
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
        if (status != PaymentStatus.INITIATED) {
            throw new IllegalStateException("Payment can only be marked as pending from INITIATED status");
        }
        status = PaymentStatus.PENDING;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentPending(id, reason, updatedAt));
    }

    public void markAsFailed(String reason) {
        if (status == PaymentStatus.FAILED) {
            return;
        }
        if (status != PaymentStatus.INITIATED && status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment can only be marked as failed from INITIATED or PENDING status");
        }
        status = PaymentStatus.FAILED;
        updatedAt = Instant.now();
        addDomainEvent(new PaymentFailed(id, reason, updatedAt));
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
