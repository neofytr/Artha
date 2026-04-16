package com.artha.domain.account;

import com.artha.core.event.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * All events that an Account aggregate can produce.
 * <p>
 * Events are immutable facts about what happened. They are named in past tense,
 * never ask permission (that's what commands do), and are serialized into the event store
 * forever — so their schema is effectively part of the public API. Changing an event's
 * shape requires an upcaster.
 */
public final class AccountEvents {

    private AccountEvents() {}

    public static final class AccountOpened extends DomainEvent {
        private final String ownerId;
        private final String currency;
        private final String initialStatus;

        public AccountOpened(UUID accountId, long sequence, String ownerId, String currency, String initialStatus) {
            super(accountId, sequence);
            this.ownerId = ownerId;
            this.currency = currency;
            this.initialStatus = initialStatus;
        }

        @JsonCreator
        public AccountOpened(@JsonProperty("eventId") UUID eventId,
                             @JsonProperty("aggregateId") UUID accountId,
                             @JsonProperty("sequenceNumber") long sequence,
                             @JsonProperty("occurredAt") Instant occurredAt,
                             @JsonProperty("ownerId") String ownerId,
                             @JsonProperty("currency") String currency,
                             @JsonProperty("initialStatus") String initialStatus) {
            super(eventId, accountId, sequence, occurredAt);
            this.ownerId = ownerId;
            this.currency = currency;
            this.initialStatus = initialStatus;
        }

        public String ownerId() { return ownerId; }
        public String currency() { return currency; }
        public String initialStatus() { return initialStatus; }
    }

    public static final class MoneyDeposited extends DomainEvent {
        private final String amount;
        private final String currency;
        private final String reference;

        public MoneyDeposited(UUID accountId, long sequence, Money money, String reference) {
            super(accountId, sequence);
            this.amount = money.amount().toPlainString();
            this.currency = money.currency().getCurrencyCode();
            this.reference = reference;
        }

        @JsonCreator
        public MoneyDeposited(@JsonProperty("eventId") UUID eventId,
                              @JsonProperty("aggregateId") UUID accountId,
                              @JsonProperty("sequenceNumber") long sequence,
                              @JsonProperty("occurredAt") Instant occurredAt,
                              @JsonProperty("amount") String amount,
                              @JsonProperty("currency") String currency,
                              @JsonProperty("reference") String reference) {
            super(eventId, accountId, sequence, occurredAt);
            this.amount = amount;
            this.currency = currency;
            this.reference = reference;
        }

        public Money money() { return Money.of(amount, currency); }
        public String amount() { return amount; }
        public String currency() { return currency; }
        public String reference() { return reference; }
    }

    public static final class MoneyWithdrawn extends DomainEvent {
        private final String amount;
        private final String currency;
        private final String reference;

        public MoneyWithdrawn(UUID accountId, long sequence, Money money, String reference) {
            super(accountId, sequence);
            this.amount = money.amount().toPlainString();
            this.currency = money.currency().getCurrencyCode();
            this.reference = reference;
        }

        @JsonCreator
        public MoneyWithdrawn(@JsonProperty("eventId") UUID eventId,
                              @JsonProperty("aggregateId") UUID accountId,
                              @JsonProperty("sequenceNumber") long sequence,
                              @JsonProperty("occurredAt") Instant occurredAt,
                              @JsonProperty("amount") String amount,
                              @JsonProperty("currency") String currency,
                              @JsonProperty("reference") String reference) {
            super(eventId, accountId, sequence, occurredAt);
            this.amount = amount;
            this.currency = currency;
            this.reference = reference;
        }

        public Money money() { return Money.of(amount, currency); }
        public String amount() { return amount; }
        public String currency() { return currency; }
        public String reference() { return reference; }
    }

    public static final class MoneyReserved extends DomainEvent {
        private final UUID reservationId;
        private final String amount;
        private final String currency;

        public MoneyReserved(UUID accountId, long sequence, UUID reservationId, Money money) {
            super(accountId, sequence);
            this.reservationId = reservationId;
            this.amount = money.amount().toPlainString();
            this.currency = money.currency().getCurrencyCode();
        }

        @JsonCreator
        public MoneyReserved(@JsonProperty("eventId") UUID eventId,
                             @JsonProperty("aggregateId") UUID accountId,
                             @JsonProperty("sequenceNumber") long sequence,
                             @JsonProperty("occurredAt") Instant occurredAt,
                             @JsonProperty("reservationId") UUID reservationId,
                             @JsonProperty("amount") String amount,
                             @JsonProperty("currency") String currency) {
            super(eventId, accountId, sequence, occurredAt);
            this.reservationId = reservationId;
            this.amount = amount;
            this.currency = currency;
        }

        public UUID reservationId() { return reservationId; }
        public Money money() { return Money.of(amount, currency); }
        public String amount() { return amount; }
        public String currency() { return currency; }
    }

    public static final class ReservationReleased extends DomainEvent {
        private final UUID reservationId;

        public ReservationReleased(UUID accountId, long sequence, UUID reservationId) {
            super(accountId, sequence);
            this.reservationId = reservationId;
        }

        @JsonCreator
        public ReservationReleased(@JsonProperty("eventId") UUID eventId,
                                    @JsonProperty("aggregateId") UUID accountId,
                                    @JsonProperty("sequenceNumber") long sequence,
                                    @JsonProperty("occurredAt") Instant occurredAt,
                                    @JsonProperty("reservationId") UUID reservationId) {
            super(eventId, accountId, sequence, occurredAt);
            this.reservationId = reservationId;
        }

        public UUID reservationId() { return reservationId; }
    }

    public static final class ReservationCommitted extends DomainEvent {
        private final UUID reservationId;

        public ReservationCommitted(UUID accountId, long sequence, UUID reservationId) {
            super(accountId, sequence);
            this.reservationId = reservationId;
        }

        @JsonCreator
        public ReservationCommitted(@JsonProperty("eventId") UUID eventId,
                                     @JsonProperty("aggregateId") UUID accountId,
                                     @JsonProperty("sequenceNumber") long sequence,
                                     @JsonProperty("occurredAt") Instant occurredAt,
                                     @JsonProperty("reservationId") UUID reservationId) {
            super(eventId, accountId, sequence, occurredAt);
            this.reservationId = reservationId;
        }

        public UUID reservationId() { return reservationId; }
    }

    public static final class AccountFrozen extends DomainEvent {
        private final String reason;

        public AccountFrozen(UUID accountId, long sequence, String reason) {
            super(accountId, sequence);
            this.reason = reason;
        }

        @JsonCreator
        public AccountFrozen(@JsonProperty("eventId") UUID eventId,
                              @JsonProperty("aggregateId") UUID accountId,
                              @JsonProperty("sequenceNumber") long sequence,
                              @JsonProperty("occurredAt") Instant occurredAt,
                              @JsonProperty("reason") String reason) {
            super(eventId, accountId, sequence, occurredAt);
            this.reason = reason;
        }

        public String reason() { return reason; }
    }

    public static final class AccountUnfrozen extends DomainEvent {
        public AccountUnfrozen(UUID accountId, long sequence) {
            super(accountId, sequence);
        }

        @JsonCreator
        public AccountUnfrozen(@JsonProperty("eventId") UUID eventId,
                                @JsonProperty("aggregateId") UUID accountId,
                                @JsonProperty("sequenceNumber") long sequence,
                                @JsonProperty("occurredAt") Instant occurredAt) {
            super(eventId, accountId, sequence, occurredAt);
        }
    }

    public static final class AccountClosed extends DomainEvent {
        public AccountClosed(UUID accountId, long sequence) {
            super(accountId, sequence);
        }

        @JsonCreator
        public AccountClosed(@JsonProperty("eventId") UUID eventId,
                              @JsonProperty("aggregateId") UUID accountId,
                              @JsonProperty("sequenceNumber") long sequence,
                              @JsonProperty("occurredAt") Instant occurredAt) {
            super(eventId, accountId, sequence, occurredAt);
        }
    }
}
