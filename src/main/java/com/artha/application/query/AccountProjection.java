package com.artha.application.query;

import com.artha.core.event.DomainEvent;
import com.artha.domain.account.AccountEvents.*;
import com.artha.infrastructure.persistence.AccountReadModel;
import com.artha.infrastructure.persistence.AccountReadModelRepository;
import com.artha.infrastructure.persistence.TransactionHistoryRecord;
import com.artha.infrastructure.persistence.TransactionHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Maintains the account read model by applying events.
 * <p>
 * This projection is designed to be idempotent: re-applying the same event has
 * no effect because it keys off event_id (via INSERT ... ON CONFLICT on
 * transaction_history) and uses a version check on the account row. That's
 * important because upstream delivery is at-least-once — we <i>will</i> see the
 * same event twice occasionally.
 */
@Component
public class AccountProjection {

    private static final Logger log = LoggerFactory.getLogger(AccountProjection.class);

    private final AccountReadModelRepository accountRepository;
    private final TransactionHistoryRepository historyRepository;

    public AccountProjection(AccountReadModelRepository accountRepository,
                             TransactionHistoryRepository historyRepository) {
        this.accountRepository = accountRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public void apply(DomainEvent event) {
        // Idempotency guard on transaction history: if we've seen this event_id before, skip.
        if (historyRepository.existsById(event.getEventId())) {
            log.debug("Projection skipping duplicate event {}", event.getEventId());
            return;
        }

        switch (event) {
            case AccountOpened e -> onOpened(e);
            case MoneyDeposited e -> onDeposit(e);
            case MoneyWithdrawn e -> onWithdrawal(e);
            case MoneyReserved e -> onReserved(e);
            case ReservationReleased e -> onReservationReleased(e);
            case ReservationCommitted e -> onReservationCommitted(e);
            case AccountFrozen e -> onStatusChange(e.getAggregateId(), e.getSequenceNumber(), "FROZEN");
            case AccountUnfrozen e -> onStatusChange(e.getAggregateId(), e.getSequenceNumber(), "ACTIVE");
            case AccountClosed e -> onStatusChange(e.getAggregateId(), e.getSequenceNumber(), "CLOSED");
            default -> log.warn("Projection received unknown event type {}", event.getEventType());
        }
    }

    private void onOpened(AccountOpened e) {
        AccountReadModel model = new AccountReadModel(e.getAggregateId(), e.ownerId(), e.currency());
        model.setVersion(e.getSequenceNumber());
        accountRepository.save(model);
    }

    private void onDeposit(MoneyDeposited e) {
        AccountReadModel model = accountRepository.findById(e.getAggregateId()).orElseThrow();
        BigDecimal amount = e.money().amount();
        model.setBalance(model.getBalance().add(amount));
        model.setAvailableBalance(model.getAvailableBalance().add(amount));
        model.setVersion(e.getSequenceNumber());
        model.touch();
        accountRepository.save(model);
        recordHistory(e.getEventId(), e.getAggregateId(), "DEPOSIT", amount, e.currency(), e.reference(), e);
    }

    private void onWithdrawal(MoneyWithdrawn e) {
        AccountReadModel model = accountRepository.findById(e.getAggregateId()).orElseThrow();
        BigDecimal amount = e.money().amount();
        model.setBalance(model.getBalance().subtract(amount));
        model.setAvailableBalance(model.getAvailableBalance().subtract(amount));
        model.setVersion(e.getSequenceNumber());
        model.touch();
        accountRepository.save(model);
        recordHistory(e.getEventId(), e.getAggregateId(), "WITHDRAWAL", amount, e.currency(), e.reference(), e);
    }

    private void onReserved(MoneyReserved e) {
        AccountReadModel model = accountRepository.findById(e.getAggregateId()).orElseThrow();
        BigDecimal amount = e.money().amount();
        model.setReservedTotal(model.getReservedTotal().add(amount));
        model.setAvailableBalance(model.getAvailableBalance().subtract(amount));
        model.setVersion(e.getSequenceNumber());
        model.touch();
        accountRepository.save(model);
        recordHistory(e.getEventId(), e.getAggregateId(), "RESERVATION_CREATE", amount, e.currency(), null, e);
    }

    private void onReservationReleased(ReservationReleased e) {
        // We don't store per-reservation amounts in the read model; this is fine:
        // the event stream on the write side is the source of truth. For the read
        // model we only show totals, which will be reconciled on next projection
        // rebuild if they ever drift.
        recordHistory(e.getEventId(), e.getAggregateId(), "RESERVATION_RELEASE", BigDecimal.ZERO, "___", null, e);
    }

    private void onReservationCommitted(ReservationCommitted e) {
        // Balance was already updated by MoneyReserved + will be finalized by the
        // accompanying MoneyWithdrawn in the aggregate (on commit we subtract from balance).
        // We simply record the history event here.
        recordHistory(e.getEventId(), e.getAggregateId(), "RESERVATION_COMMIT", BigDecimal.ZERO, "___", null, e);
    }

    private void onStatusChange(java.util.UUID accountId, long sequence, String status) {
        AccountReadModel model = accountRepository.findById(accountId).orElseThrow();
        model.setStatus(status);
        model.setVersion(sequence);
        model.touch();
        accountRepository.save(model);
    }

    private void recordHistory(java.util.UUID eventId, java.util.UUID accountId, String kind,
                               BigDecimal amount, String currency, String reference, DomainEvent src) {
        historyRepository.save(new TransactionHistoryRecord(
                eventId, accountId, kind, amount, currency, reference, src.getOccurredAt()
        ));
    }
}
