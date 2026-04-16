package com.artha.application.command;

import com.artha.core.cqrs.Command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Commands that operate on Account aggregates. Each command has a single
 * handler; see {@link AccountCommandHandlers}.
 */
public final class AccountCommands {

    private AccountCommands() {}

    public record OpenAccount(UUID accountId, String ownerId, String currency) implements Command<UUID> {}

    public record Deposit(UUID accountId, BigDecimal amount, String currency, String reference)
            implements Command<Void> {}

    public record Withdraw(UUID accountId, BigDecimal amount, String currency, String reference)
            implements Command<Void> {}

    public record TransferMoney(UUID sourceAccountId, UUID destinationAccountId,
                                BigDecimal amount, String currency, String reference)
            implements Command<UUID> {}  // returns transferId

    public record FreezeAccount(UUID accountId, String reason) implements Command<Void> {}

    public record UnfreezeAccount(UUID accountId) implements Command<Void> {}

    public record CloseAccount(UUID accountId) implements Command<Void> {}
}
