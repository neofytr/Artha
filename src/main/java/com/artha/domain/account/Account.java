package com.artha.domain.account;

import com.artha.core.aggregate.AggregateRoot;
import com.artha.core.aggregate.Snapshotable;
import com.artha.core.event.DomainEvent;
import com.artha.domain.account.AccountEvents.*;

import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Account aggregate.
 * <p>
 * Invariants enforced here:
 * <ul>
 *   <li>Balance never goes negative (no overdraft).</li>
 *   <li>Reserved funds are included in "available balance" arithmetic but not the raw balance.</li>
 *   <li>Frozen or closed accounts reject all money movement.</li>
 *   <li>All deposits/withdrawals must match the account currency.</li>
 * </ul>
 * <p>
 * The aggregate reconstructs its state purely from its event stream. Commands
 * are validated against current state, then (if valid) produce one or more events.
 */
public class Account extends AggregateRoot implements Snapshotable<AccountSnapshot> {

    public enum Status { ACTIVE, FROZEN, CLOSED }

    private String ownerId;
    private Currency currency;
    private Money balance;                       // cleared balance
    private final Map<UUID, Money> reservations = new HashMap<>();
    private Status status;

    public Account() {
        super();
    }

    // ------- Commands (factory + behavior methods) -------

    public static Account open(UUID accountId, String ownerId, Currency currency) {
        Account account = new Account();
        account.setId(accountId);
        account.raise(new AccountOpened(accountId, 1, ownerId, currency.getCurrencyCode(), Status.ACTIVE.name()));
        return account;
    }

    public void deposit(Money amount, String reference) {
        requireActive();
        requireSameCurrency(amount);
        if (!amount.isPositive()) {
            throw new DomainException("Deposit amount must be positive");
        }
        raise(new MoneyDeposited(getId(), nextSequence(), amount, reference));
    }

    public void withdraw(Money amount, String reference) {
        requireActive();
        requireSameCurrency(amount);
        if (!amount.isPositive()) {
            throw new DomainException("Withdrawal amount must be positive");
        }
        if (availableBalance().isLessThan(amount)) {
            throw new InsufficientFundsException(getId(), availableBalance(), amount);
        }
        raise(new MoneyWithdrawn(getId(), nextSequence(), amount, reference));
    }

    public void reserve(UUID reservationId, Money amount) {
        requireActive();
        requireSameCurrency(amount);
        if (!amount.isPositive()) {
            throw new DomainException("Reservation amount must be positive");
        }
        if (reservations.containsKey(reservationId)) {
            throw new DomainException("Reservation " + reservationId + " already exists");
        }
        if (availableBalance().isLessThan(amount)) {
            throw new InsufficientFundsException(getId(), availableBalance(), amount);
        }
        raise(new MoneyReserved(getId(), nextSequence(), reservationId, amount));
    }

    public void releaseReservation(UUID reservationId) {
        if (!reservations.containsKey(reservationId)) {
            // Idempotent: if already released, do nothing.
            return;
        }
        raise(new ReservationReleased(getId(), nextSequence(), reservationId));
    }

    public void commitReservation(UUID reservationId) {
        Money reserved = reservations.get(reservationId);
        if (reserved == null) {
            throw new DomainException("Reservation " + reservationId + " not found");
        }
        raise(new ReservationCommitted(getId(), nextSequence(), reservationId));
    }

    public void freeze(String reason) {
        if (status == Status.FROZEN) return;
        if (status == Status.CLOSED) throw new DomainException("Cannot freeze a closed account");
        raise(new AccountFrozen(getId(), nextSequence(), reason));
    }

    public void unfreeze() {
        if (status == Status.ACTIVE) return;
        if (status == Status.CLOSED) throw new DomainException("Cannot unfreeze a closed account");
        raise(new AccountUnfrozen(getId(), nextSequence()));
    }

    public void close() {
        if (status == Status.CLOSED) return;
        if (!reservations.isEmpty()) throw new DomainException("Cannot close account with active reservations");
        if (!balance.isZero()) throw new DomainException("Cannot close account with non-zero balance");
        raise(new AccountClosed(getId(), nextSequence()));
    }

    // ------- Event application -------

    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case AccountOpened e -> {
                setId(e.getAggregateId());
                this.ownerId = e.ownerId();
                this.currency = Currency.getInstance(e.currency());
                this.balance = Money.zero(this.currency);
                this.status = Status.valueOf(e.initialStatus());
            }
            case MoneyDeposited e -> this.balance = this.balance.add(e.money());
            case MoneyWithdrawn e -> this.balance = this.balance.subtract(e.money());
            case MoneyReserved e -> this.reservations.put(e.reservationId(), e.money());
            case ReservationReleased e -> this.reservations.remove(e.reservationId());
            case ReservationCommitted e -> {
                Money amount = this.reservations.remove(e.reservationId());
                if (amount != null) {
                    this.balance = this.balance.subtract(amount);
                }
            }
            case AccountFrozen e -> this.status = Status.FROZEN;
            case AccountUnfrozen e -> this.status = Status.ACTIVE;
            case AccountClosed e -> this.status = Status.CLOSED;
            default -> throw new IllegalStateException("Unknown event type: " + event.getEventType());
        }
    }

    // ------- Guards -------

    private void requireActive() {
        if (status == Status.FROZEN) throw new DomainException("Account is frozen");
        if (status == Status.CLOSED) throw new DomainException("Account is closed");
    }

    private void requireSameCurrency(Money amount) {
        if (!amount.currency().equals(this.currency)) {
            throw new Money.CurrencyMismatchException(this.currency, amount.currency());
        }
    }

    // ------- Read-only accessors -------

    public String getOwnerId() { return ownerId; }
    public Currency getCurrency() { return currency; }
    public Money getBalance() { return balance; }
    public Status getStatus() { return status; }

    /** Balance minus reserved funds. This is what a user can spend. */
    public Money availableBalance() {
        Money total = Money.zero(currency);
        for (Money m : reservations.values()) total = total.add(m);
        return balance.subtract(total);
    }

    public Map<UUID, Money> getReservations() {
        return Map.copyOf(reservations);
    }

    // ------- Snapshot -------

    @Override
    public AccountSnapshot captureSnapshot() {
        Map<UUID, String> serializedReservations = new HashMap<>();
        reservations.forEach((k, v) -> serializedReservations.put(k, v.amount().toPlainString()));
        return new AccountSnapshot(
                getId(), ownerId, currency.getCurrencyCode(),
                balance.amount().toPlainString(),
                serializedReservations, status.name()
        );
    }

    @Override
    public void restoreFromSnapshot(AccountSnapshot snapshot) {
        setId(snapshot.accountId());
        this.ownerId = snapshot.ownerId();
        this.currency = Currency.getInstance(snapshot.currency());
        this.balance = Money.of(snapshot.balance(), snapshot.currency());
        this.reservations.clear();
        snapshot.reservations().forEach((k, v) -> reservations.put(k, Money.of(v, snapshot.currency())));
        this.status = Status.valueOf(snapshot.status());
    }

    @Override
    public Class<AccountSnapshot> snapshotType() {
        return AccountSnapshot.class;
    }
}
