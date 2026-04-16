package com.artha.domain.transfer;

import com.artha.domain.account.Money;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/**
 * Context threaded through the TransferMoney saga. Mutable so steps can record
 * the IDs of partial work (e.g. reservation ID) that compensation needs to undo.
 * <p>
 * Serialized to JSON for durable saga state — the extra Jackson annotations keep
 * the domain type clean while letting the store round-trip it.
 */
public class TransferContext {

    private final UUID transferId;
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final String amount;
    private final String currency;
    private final String reference;

    private UUID sourceReservationId;
    private boolean destinationCredited;

    public TransferContext(UUID transferId,
                           UUID sourceAccountId,
                           UUID destinationAccountId,
                           Money amount,
                           String reference) {
        this(transferId, sourceAccountId, destinationAccountId,
                amount.amount().toPlainString(), amount.currency().getCurrencyCode(), reference);
    }

    @JsonCreator
    public TransferContext(@JsonProperty("transferId") UUID transferId,
                           @JsonProperty("sourceAccountId") UUID sourceAccountId,
                           @JsonProperty("destinationAccountId") UUID destinationAccountId,
                           @JsonProperty("amount") String amount,
                           @JsonProperty("currency") String currency,
                           @JsonProperty("reference") String reference) {
        this.transferId = transferId;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
    }

    public UUID transferId() { return transferId; }
    public UUID sourceAccountId() { return sourceAccountId; }
    public UUID destinationAccountId() { return destinationAccountId; }
    public Money amount() { return Money.of(new BigDecimal(amount), Currency.getInstance(currency)); }
    public String getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String reference() { return reference; }

    public UUID sourceReservationId() { return sourceReservationId; }
    public void setSourceReservationId(UUID id) { this.sourceReservationId = id; }

    public boolean isDestinationCredited() { return destinationCredited; }
    public void setDestinationCredited(boolean v) { this.destinationCredited = v; }
}
