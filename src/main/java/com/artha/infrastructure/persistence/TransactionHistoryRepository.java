package com.artha.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistoryRecord, UUID> {
    Page<TransactionHistoryRecord> findByAccountIdOrderByOccurredAtDesc(UUID accountId, Pageable pageable);
}
