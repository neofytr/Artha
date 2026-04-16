package com.artha.domain.account;

import java.util.UUID;

public class InsufficientFundsException extends DomainException {
    private final UUID accountId;
    private final Money available;
    private final Money requested;

    public InsufficientFundsException(UUID accountId, Money available, Money requested) {
        super("Insufficient funds on account %s: available %s, requested %s"
                .formatted(accountId, available, requested));
        this.accountId = accountId;
        this.available = available;
        this.requested = requested;
    }

    public UUID getAccountId() { return accountId; }
    public Money getAvailable() { return available; }
    public Money getRequested() { return requested; }
}
