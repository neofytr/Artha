package com.artha.domain.account;

import com.artha.core.event.DomainEvent;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests on the Account aggregate.
 * <p>
 * These tests use random sequences of operations to explore the state space
 * far more thoroughly than example-based tests. For each generated sequence
 * we assert two safety properties:
 * <ol>
 *   <li><b>Balance is never negative.</b> The no-overdraft invariant must hold under
 *       any legal sequence of operations.</li>
 *   <li><b>Replay determinism.</b> Replaying the event stream produces the same
 *       state as the originally-executed operations. This is the foundation of
 *       event sourcing — if replay drifts from the live aggregate, nothing else
 *       matters.</li>
 * </ol>
 */
class AccountPropertyTest {

    enum Op { DEPOSIT, WITHDRAW, RESERVE, COMMIT, RELEASE, FREEZE, UNFREEZE }

    @Property(tries = 500)
    void balanceNeverGoesNegative(@ForAll @Size(min = 1, max = 200) List<@From("operation") Op> ops,
                                  @ForAll("amounts") List<BigDecimal> amounts) {
        Account account = Account.open(UUID.randomUUID(), "prop", Currency.getInstance("INR"));
        Deque<UUID> liveReservations = new ArrayDeque<>();

        int amountIdx = 0;
        for (Op op : ops) {
            BigDecimal amount = amounts.get(amountIdx++ % amounts.size());
            Money m = Money.of(amount, Currency.getInstance("INR"));
            try {
                switch (op) {
                    case DEPOSIT -> account.deposit(m, "rand");
                    case WITHDRAW -> account.withdraw(m, "rand");
                    case RESERVE -> {
                        UUID id = UUID.randomUUID();
                        account.reserve(id, m);
                        liveReservations.push(id);
                    }
                    case COMMIT -> {
                        if (!liveReservations.isEmpty()) account.commitReservation(liveReservations.pop());
                    }
                    case RELEASE -> {
                        if (!liveReservations.isEmpty()) account.releaseReservation(liveReservations.pop());
                    }
                    case FREEZE -> account.freeze("random");
                    case UNFREEZE -> account.unfreeze();
                }
            } catch (RuntimeException ignored) {
                // Business rule violations (insufficient funds, frozen account, etc.)
                // don't invalidate the property — they just mean the aggregate correctly
                // refused an illegal operation.
            }

            // Invariant: balance and available balance are non-negative.
            assertThat(account.getBalance().isNegative()).isFalse();
            assertThat(account.availableBalance().isNegative()).isFalse();
        }
    }

    @Property(tries = 200)
    void replayReproducesState(@ForAll @Size(min = 1, max = 100) List<@From("operation") Op> ops,
                               @ForAll("amounts") List<BigDecimal> amounts) {
        UUID id = UUID.randomUUID();
        Account source = Account.open(id, "prop", Currency.getInstance("INR"));
        Deque<UUID> liveReservations = new ArrayDeque<>();

        int amountIdx = 0;
        for (Op op : ops) {
            BigDecimal amount = amounts.get(amountIdx++ % amounts.size());
            Money m = Money.of(amount, Currency.getInstance("INR"));
            try {
                switch (op) {
                    case DEPOSIT -> source.deposit(m, "r");
                    case WITHDRAW -> source.withdraw(m, "r");
                    case RESERVE -> {
                        UUID rid = UUID.randomUUID();
                        source.reserve(rid, m);
                        liveReservations.push(rid);
                    }
                    case COMMIT -> {
                        if (!liveReservations.isEmpty()) source.commitReservation(liveReservations.pop());
                    }
                    case RELEASE -> {
                        if (!liveReservations.isEmpty()) source.releaseReservation(liveReservations.pop());
                    }
                    case FREEZE -> source.freeze("r");
                    case UNFREEZE -> source.unfreeze();
                }
            } catch (RuntimeException ignored) {}
        }

        List<DomainEvent> history = List.copyOf(source.getUncommittedEvents());
        Account replayed = new Account();
        replayed.replay(history);

        assertThat(replayed.getBalance()).isEqualTo(source.getBalance());
        assertThat(replayed.availableBalance()).isEqualTo(source.availableBalance());
        assertThat(replayed.getStatus()).isEqualTo(source.getStatus());
        assertThat(replayed.getReservations().size()).isEqualTo(source.getReservations().size());
    }

    @Property(tries = 100)
    void snapshotRoundTripPreservesState(@ForAll @Size(min = 1, max = 50) List<@From("operation") Op> ops,
                                         @ForAll("amounts") List<BigDecimal> amounts) {
        Account source = Account.open(UUID.randomUUID(), "prop", Currency.getInstance("INR"));
        Deque<UUID> liveReservations = new ArrayDeque<>();
        int i = 0;
        for (Op op : ops) {
            BigDecimal amount = amounts.get(i++ % amounts.size());
            Money m = Money.of(amount, Currency.getInstance("INR"));
            try {
                switch (op) {
                    case DEPOSIT -> source.deposit(m, "r");
                    case WITHDRAW -> source.withdraw(m, "r");
                    case RESERVE -> { UUID rid = UUID.randomUUID(); source.reserve(rid, m); liveReservations.push(rid); }
                    case COMMIT -> { if (!liveReservations.isEmpty()) source.commitReservation(liveReservations.pop()); }
                    case RELEASE -> { if (!liveReservations.isEmpty()) source.releaseReservation(liveReservations.pop()); }
                    case FREEZE -> source.freeze("r");
                    case UNFREEZE -> source.unfreeze();
                }
            } catch (RuntimeException ignored) {}
        }
        AccountSnapshot snap = source.captureSnapshot();
        Account restored = new Account();
        restored.restoreFromSnapshot(snap);
        assertThat(restored.getBalance()).isEqualTo(source.getBalance());
        assertThat(restored.availableBalance()).isEqualTo(source.availableBalance());
    }

    @Provide
    Arbitrary<Op> operation() {
        return Arbitraries.of(Op.values());
    }

    @Provide
    Arbitrary<List<BigDecimal>> amounts() {
        return Arbitraries.integers().between(1, 1000)
                .map(BigDecimal::valueOf)
                .list().ofMinSize(1).ofMaxSize(20);
    }

    @Property(tries = 50)
    void ledgerConservation(@ForAll @IntRange(min = 2, max = 6) int accountCount,
                            @ForAll @IntRange(min = 10, max = 200) int transfers) {
        Currency INR = Currency.getInstance("INR");
        List<Account> accounts = new ArrayList<>();
        BigDecimal startingBalance = BigDecimal.valueOf(10_000);
        for (int i = 0; i < accountCount; i++) {
            Account a = Account.open(UUID.randomUUID(), "user-" + i, INR);
            a.deposit(Money.of(startingBalance, INR), "seed");
            accounts.add(a);
        }
        BigDecimal initialTotal = startingBalance.multiply(BigDecimal.valueOf(accountCount))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        Random rng = new Random(42);
        for (int i = 0; i < transfers; i++) {
            Account src = accounts.get(rng.nextInt(accountCount));
            Account dst = accounts.get(rng.nextInt(accountCount));
            if (src == dst) continue;
            BigDecimal amount = BigDecimal.valueOf(rng.nextInt(500) + 1);
            Money m = Money.of(amount, INR);

            // Simulate a transfer as atomic pair of withdraw+deposit (the aggregate-level
            // equivalent of what the saga does; here we test money conservation at the domain level).
            try {
                src.withdraw(m, "t" + i);
                dst.deposit(m, "t" + i);
            } catch (InsufficientFundsException e) {
                // expected: src didn't have enough. No state change.
            }
        }

        BigDecimal total = accounts.stream()
                .map(a -> a.getBalance().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        // Money is conserved: no transfer ever creates or destroys rupees.
        assertThat(total).isEqualByComparingTo(initialTotal);
    }
}
