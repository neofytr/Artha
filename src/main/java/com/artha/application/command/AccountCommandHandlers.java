package com.artha.application.command;

import com.artha.application.command.AccountCommands.*;
import com.artha.application.saga.TransferMoneySaga;
import com.artha.core.aggregate.AggregateRepository;
import com.artha.core.event.ConcurrencyException;
import com.artha.core.resilience.RetryPolicy;
import com.artha.core.saga.SagaOrchestrator;
import com.artha.domain.account.Account;
import com.artha.domain.account.DomainException;
import com.artha.domain.account.Money;
import com.artha.domain.transfer.TransferContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.UUID;

/**
 * Handlers translate commands into aggregate behavior + persistence. They run
 * inside a transaction that spans (load events, validate, append new events,
 * write outbox). A concurrency loss is retried with backoff — optimistic
 * concurrency is expected under contention, not an error condition.
 */
@Component
public class AccountCommandHandlers {

    private final AggregateRepository<Account> accounts;
    private final SagaOrchestrator<TransferContext> transferOrchestrator;
    private final RetryPolicy concurrencyRetry;

    public AccountCommandHandlers(AggregateRepository<Account> accounts,
                                  SagaOrchestrator<TransferContext> transferOrchestrator,
                                  RetryPolicy concurrencyRetry) {
        this.accounts = accounts;
        this.transferOrchestrator = transferOrchestrator;
        this.concurrencyRetry = concurrencyRetry;
    }

    @Transactional
    public UUID handle(OpenAccount cmd) {
        return concurrencyRetry.execute(() -> {
            if (accounts.load(cmd.accountId()).isPresent()) {
                throw new DomainException("Account already exists: " + cmd.accountId());
            }
            Account account = Account.open(cmd.accountId(), cmd.ownerId(), Currency.getInstance(cmd.currency()));
            accounts.save(account);
            return cmd.accountId();
        });
    }

    @Transactional
    public Void handle(Deposit cmd) {
        return concurrencyRetry.execute(() -> {
            Account account = accounts.load(cmd.accountId())
                    .orElseThrow(() -> new DomainException("Account not found: " + cmd.accountId()));
            account.deposit(Money.of(cmd.amount(), Currency.getInstance(cmd.currency())), cmd.reference());
            accounts.save(account);
            return null;
        });
    }

    @Transactional
    public Void handle(Withdraw cmd) {
        return concurrencyRetry.execute(() -> {
            Account account = accounts.load(cmd.accountId())
                    .orElseThrow(() -> new DomainException("Account not found: " + cmd.accountId()));
            account.withdraw(Money.of(cmd.amount(), Currency.getInstance(cmd.currency())), cmd.reference());
            accounts.save(account);
            return null;
        });
    }

    public UUID handle(TransferMoney cmd) {
        UUID transferId = UUID.randomUUID();
        TransferContext ctx = new TransferContext(
                transferId, cmd.sourceAccountId(), cmd.destinationAccountId(),
                Money.of(cmd.amount(), Currency.getInstance(cmd.currency())), cmd.reference()
        );
        SagaOrchestrator.SagaResult result = transferOrchestrator.run(transferId, ctx);
        if (result.state().name().equals("FAILED") || result.state().name().equals("STUCK")) {
            throw new DomainException("Transfer failed: " + result.error());
        }
        return transferId;
    }

    @Transactional
    public Void handle(FreezeAccount cmd) {
        return concurrencyRetry.execute(() -> {
            Account account = accounts.load(cmd.accountId()).orElseThrow(() -> new DomainException("Account not found"));
            account.freeze(cmd.reason());
            accounts.save(account);
            return null;
        });
    }

    @Transactional
    public Void handle(UnfreezeAccount cmd) {
        return concurrencyRetry.execute(() -> {
            Account account = accounts.load(cmd.accountId()).orElseThrow(() -> new DomainException("Account not found"));
            account.unfreeze();
            accounts.save(account);
            return null;
        });
    }

    @Transactional
    public Void handle(CloseAccount cmd) {
        return concurrencyRetry.execute(() -> {
            Account account = accounts.load(cmd.accountId()).orElseThrow(() -> new DomainException("Account not found"));
            account.close();
            accounts.save(account);
            return null;
        });
    }
}
