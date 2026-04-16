package com.artha.application.saga;

import com.artha.core.aggregate.AggregateRepository;
import com.artha.core.saga.SagaDefinition;
import com.artha.core.saga.SagaStep;
import com.artha.domain.account.Account;
import com.artha.domain.account.DomainException;
import com.artha.domain.transfer.TransferContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Moves money between two accounts as a saga:
 * <ol>
 *   <li><b>Reserve</b> the amount on the source account. Compensation: release reservation.</li>
 *   <li><b>Credit</b> the destination account. Compensation: debit the destination.</li>
 *   <li><b>Commit</b> the source reservation (turns reserve → actual debit). No compensation —
 *       at this point the transfer is effectively complete and idempotent.</li>
 * </ol>
 * <p>
 * Why reserve-then-credit-then-commit instead of "debit source, credit dest"? Because with a
 * naive debit-first pattern, the compensation of a failed credit (re-credit source) can race
 * with another withdrawal from source, breaking the no-overdraft invariant. Reservations
 * isolate the at-risk funds while the cross-aggregate work happens.
 */
public class TransferMoneySaga {

    private static final Logger log = LoggerFactory.getLogger(TransferMoneySaga.class);

    public static SagaDefinition<TransferContext> define(AggregateRepository<Account> accounts) {
        return SagaDefinition.<TransferContext>named("TransferMoney")
                .step(new ReserveSourceStep(accounts))
                .step(new CreditDestinationStep(accounts))
                .step(new CommitSourceReservationStep(accounts))
                .build();
    }

    // ---- Step 1: reserve source ----
    static final class ReserveSourceStep implements SagaStep<TransferContext> {
        private final AggregateRepository<Account> accounts;

        ReserveSourceStep(AggregateRepository<Account> accounts) { this.accounts = accounts; }

        @Override public String name() { return "ReserveSource"; }

        @Override
        public void execute(TransferContext ctx) {
            Account source = accounts.load(ctx.sourceAccountId())
                    .orElseThrow(() -> new DomainException("Source account not found: " + ctx.sourceAccountId()));
            UUID reservationId = UUID.randomUUID();
            source.reserve(reservationId, ctx.amount());
            accounts.save(source);
            ctx.setSourceReservationId(reservationId);
            log.info("Reserved {} on source {} (reservation {})", ctx.amount(), ctx.sourceAccountId(), reservationId);
        }

        @Override
        public void compensate(TransferContext ctx) {
            if (ctx.sourceReservationId() == null) return; // never reserved
            Account source = accounts.load(ctx.sourceAccountId()).orElseThrow();
            source.releaseReservation(ctx.sourceReservationId());
            accounts.save(source);
            log.info("Released reservation {} on source {}", ctx.sourceReservationId(), ctx.sourceAccountId());
        }
    }

    // ---- Step 2: credit destination ----
    static final class CreditDestinationStep implements SagaStep<TransferContext> {
        private final AggregateRepository<Account> accounts;

        CreditDestinationStep(AggregateRepository<Account> accounts) { this.accounts = accounts; }

        @Override public String name() { return "CreditDestination"; }

        @Override
        public void execute(TransferContext ctx) {
            Account dest = accounts.load(ctx.destinationAccountId())
                    .orElseThrow(() -> new DomainException("Destination account not found: " + ctx.destinationAccountId()));
            dest.deposit(ctx.amount(), "transfer:" + ctx.transferId());
            accounts.save(dest);
            ctx.setDestinationCredited(true);
            log.info("Credited {} to destination {}", ctx.amount(), ctx.destinationAccountId());
        }

        @Override
        public void compensate(TransferContext ctx) {
            if (!ctx.isDestinationCredited()) return;
            Account dest = accounts.load(ctx.destinationAccountId()).orElseThrow();
            dest.withdraw(ctx.amount(), "transfer-compensation:" + ctx.transferId());
            accounts.save(dest);
            log.info("Reversed credit of {} on destination {}", ctx.amount(), ctx.destinationAccountId());
        }
    }

    // ---- Step 3: commit source reservation ----
    static final class CommitSourceReservationStep implements SagaStep<TransferContext> {
        private final AggregateRepository<Account> accounts;

        CommitSourceReservationStep(AggregateRepository<Account> accounts) { this.accounts = accounts; }

        @Override public String name() { return "CommitSourceReservation"; }

        @Override
        public void execute(TransferContext ctx) {
            Account source = accounts.load(ctx.sourceAccountId()).orElseThrow();
            source.commitReservation(ctx.sourceReservationId());
            accounts.save(source);
            log.info("Committed reservation {} on source {}", ctx.sourceReservationId(), ctx.sourceAccountId());
        }

        @Override
        public void compensate(TransferContext ctx) {
            // No-op: by the time we commit, the transfer is settled. Any later
            // failure is outside the saga's transactional boundary (e.g. notification).
        }
    }
}
