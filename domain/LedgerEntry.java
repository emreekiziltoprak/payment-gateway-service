package domain;

import java.time.Instant;

public class LedgerEntry {
    private final LedgerEntryId id;
    private final PaymentId paymentId;
    private final AccountId accountId;
    private final Money amount;
    private final EntryType entryType;
    private final Instant timestamp;

    public LedgerEntry(LedgerEntryId id, PaymentId paymentId, AccountId accountId, 
                      Money amount, EntryType entryType, Instant timestamp) {
        this.id = id;
        this.paymentId = paymentId;
        this.accountId = accountId;
        this.amount = amount;
        this.entryType = entryType;
        this.timestamp = timestamp;
    }

    // Getters
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
