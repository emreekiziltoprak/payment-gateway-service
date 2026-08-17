package domain;

import domain.event.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Payment {
    private final PaymentId id;
    private final AccountId sourceAccountId;
    private final AccountId destinationAccountId;
    private final Money amount;
    private PaymentStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<PaymentEvent> domainEvents = new ArrayList<>();

    public Payment(PaymentId id, AccountId sourceAccountId, AccountId destinationAccountId, Money amount) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.status = PaymentStatus.INITIATED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        
        addDomainEvent(new PaymentInitiated(id, sourceAccountId, destinationAccountId, amount, createdAt));
    }

    public void markAsSucceeded() {
        if (this.status != PaymentStatus.INITIATED) {
            throw new IllegalStateException("Payment can only be marked as succeeded from INITIATED status");
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.updatedAt = Instant.now();
        addDomainEvent(new PaymentSucceeded(id, updatedAt));
    }

    public void markAsFailed(String reason) {
        if (this.status != PaymentStatus.INITIATED) {
            throw new IllegalStateException("Payment can only be marked as failed from INITIATED status");
        }
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
        addDomainEvent(new PaymentFailed(id, reason, updatedAt));
    }

    public void refund() {
        if (this.status != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Only succeeded payments can be refunded");
        }
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = Instant.now();
        addDomainEvent(new PaymentRefunded(id, updatedAt));
    }

    private void addDomainEvent(PaymentEvent event) {
        domainEvents.add(event);
    }

    public List<PaymentEvent> getDomainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // Getters
    public PaymentId getId() {
        return id;
    }

    public AccountId getSourceAccountId() {
        return sourceAccountId;
    }

    public AccountId getDestinationAccountId() {
        return destinationAccountId;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
