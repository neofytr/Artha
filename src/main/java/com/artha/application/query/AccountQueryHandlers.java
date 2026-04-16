package com.artha.application.query;

import com.artha.application.query.AccountQueries.*;
import com.artha.infrastructure.persistence.AccountReadModel;
import com.artha.infrastructure.persistence.AccountReadModelRepository;
import com.artha.infrastructure.persistence.TransactionHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class AccountQueryHandlers {

    private final AccountReadModelRepository accountRepository;
    private final TransactionHistoryRepository historyRepository;

    public AccountQueryHandlers(AccountReadModelRepository accountRepository,
                                TransactionHistoryRepository historyRepository) {
        this.accountRepository = accountRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public AccountView handle(GetAccount q) {
        return accountRepository.findById(q.accountId()).map(this::toView).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AccountView> handle(ListAccountsByOwner q) {
        return accountRepository.findByOwnerId(q.ownerId()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionView> handle(GetTransactionHistory q) {
        return historyRepository
                .findByAccountIdOrderByOccurredAtDesc(q.accountId(), PageRequest.of(q.page(), q.size()))
                .map(r -> new TransactionView(
                        r.getEventId(), r.getAccountId(), r.getKind(), r.getAmount(),
                        r.getCurrency(), r.getReference(), r.getOccurredAt()))
                .toList();
    }

    private AccountView toView(AccountReadModel m) {
        return new AccountView(
                m.getAccountId(), m.getOwnerId(), m.getCurrency(),
                m.getBalance(), m.getAvailableBalance(), m.getReservedTotal(),
                m.getStatus(), m.getVersion(), m.getUpdatedAt()
        );
    }
}
