package com.artha.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_read_model")
public class AccountReadModel {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 32, scale = 8)
    private BigDecimal balance;

    @Column(name = "available_balance", nullable = false, precision = 32, scale = 8)
    private BigDecimal availableBalance;

    @Column(name = "reserved_total", nullable = false, precision = 32, scale = 8)
    private BigDecimal reservedTotal;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountReadModel() {}

    public AccountReadModel(UUID accountId, String ownerId, String currency) {
        this.accountId = accountId;
        this.ownerId = ownerId;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
        this.availableBalance = BigDecimal.ZERO;
        this.reservedTotal = BigDecimal.ZERO;
        this.status = "ACTIVE";
        this.version = 0;
        this.updatedAt = Instant.now();
    }

    public UUID getAccountId() { return accountId; }
    public String getOwnerId() { return ownerId; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public BigDecimal getReservedTotal() { return reservedTotal; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }
    public void setReservedTotal(BigDecimal reservedTotal) { this.reservedTotal = reservedTotal; }
    public void setStatus(String status) { this.status = status; }
    public void setVersion(long version) { this.version = version; }
    public void touch() { this.updatedAt = Instant.now(); }
}
