package com.artha.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SagaInstanceRecordRepository extends JpaRepository<SagaInstanceRecord, UUID> {
    List<SagaInstanceRecord> findByStateIn(List<String> states);
    long countByState(String state);
}
