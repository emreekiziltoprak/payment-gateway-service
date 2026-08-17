package com.emrekiziltoprak.payment.gateway.service.domain;

import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentEvent;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentFailed;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentInitiated;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentRefunded;
import com.emrekiziltoprak.payment.gateway.service.domain.event.PaymentSucceeded;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Payment {

	private final PaymentId id;
	private final AccountId sourceAccountId;
	private final AccountId destinationAccountId;
	private final Money amount;
	private final Instant createdAt;
	private final List<PaymentEvent> domainEvents = new ArrayList<>();

	private PaymentStatus status;
	private Instant updatedAt;

	public Payment(PaymentId id, AccountId sourceAccountId, AccountId destinationAccountId, Money amount) {
		this.id = Objects.requireNonNull(id, "id cannot be null");
		this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId cannot be null");
		this.destinationAccountId = Objects.requireNonNull(destinationAccountId, "destinationAccountId cannot be null");
		this.amount = Objects.requireNonNull(amount, "amount cannot be null");
		this.status = PaymentStatus.INITIATED;
		this.createdAt = Instant.now();
		this.updatedAt = createdAt;

		addDomainEvent(new PaymentInitiated(id, sourceAccountId, destinationAccountId, amount, createdAt));
	}

	public void markAsSucceeded() {
		if (status != PaymentStatus.INITIATED) {
			throw new IllegalStateException("Payment can only be marked as succeeded from INITIATED status");
		}
		status = PaymentStatus.SUCCEEDED;
		updatedAt = Instant.now();
		addDomainEvent(new PaymentSucceeded(id, updatedAt));
	}

	public void markAsFailed(String reason) {
		if (status != PaymentStatus.INITIATED) {
			throw new IllegalStateException("Payment can only be marked as failed from INITIATED status");
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

	private void addDomainEvent(PaymentEvent event) {
		domainEvents.add(event);
	}
}
