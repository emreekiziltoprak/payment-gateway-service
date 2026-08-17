package com.emrekiziltoprak.payment.gateway.service.domain;

import java.time.Instant;
import java.util.Objects;

public class LedgerEntry {

	private final LedgerEntryId id;
	private final PaymentId paymentId;
	private final AccountId accountId;
	private final Money amount;
	private final EntryType entryType;
	private final Instant timestamp;

	public LedgerEntry(
			LedgerEntryId id,
			PaymentId paymentId,
			AccountId accountId,
			Money amount,
			EntryType entryType,
			Instant timestamp) {
		this.id = Objects.requireNonNull(id, "id cannot be null");
		this.paymentId = Objects.requireNonNull(paymentId, "paymentId cannot be null");
		this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
		this.amount = Objects.requireNonNull(amount, "amount cannot be null");
		this.entryType = Objects.requireNonNull(entryType, "entryType cannot be null");
		this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
	}

	public LedgerEntryId getId() {
		return id;
	}

	public PaymentId getPaymentId() {
		return paymentId;
	}

	public AccountId getAccountId() {
		return accountId;
	}

	public Money getAmount() {
		return amount;
	}

	public EntryType getEntryType() {
		return entryType;
	}

	public Instant getTimestamp() {
		return timestamp;
	}
}
