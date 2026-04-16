package com.artha.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_history")
public class TransactionHistoryRecord {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "amount", nullable = false, precision = 32, scale = 8)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "reference")
    private String reference;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TransactionHistoryRecord() {}

    public TransactionHistoryRecord(UUID eventId, UUID accountId, String kind,
                                    BigDecimal amount, String currency, String reference,
                                    Instant occurredAt) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.kind = kind;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.occurredAt = occurredAt;
    }

    public UUID getEventId() { return eventId; }
    public UUID getAccountId() { return accountId; }
    public String getKind() { return kind; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReference() { return reference; }
    public Instant getOccurredAt() { return occurredAt; }
}
