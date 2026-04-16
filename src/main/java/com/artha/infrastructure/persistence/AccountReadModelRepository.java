package com.artha.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountReadModelRepository extends JpaRepository<AccountReadModel, UUID> {
    List<AccountReadModel> findByOwnerId(String ownerId);
}
