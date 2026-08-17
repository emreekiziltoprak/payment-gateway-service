package com.emrekiziltoprak.payment.gateway.service.domain;

import java.util.UUID;

public record LedgerEntryId(UUID value) {

	public LedgerEntryId {
		if (value == null) {
			throw new IllegalArgumentException("LedgerEntryId cannot be null");
		}
	}

	public static LedgerEntryId generate() {
		return new LedgerEntryId(UUID.randomUUID());
	}
}
