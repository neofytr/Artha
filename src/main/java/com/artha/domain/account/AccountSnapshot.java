package com.artha.domain.account;

import java.util.Map;
import java.util.UUID;

/** Serializable snapshot of Account state. */
public record AccountSnapshot(
        UUID accountId,
        String ownerId,
        String currency,
        String balance,
        Map<UUID, String> reservations,
        String status
) {}
