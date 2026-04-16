package com.artha.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountView(
        UUID accountId,
        String ownerId,
        String currency,
        BigDecimal balance,
        BigDecimal availableBalance,
        BigDecimal reservedTotal,
        String status,
        long version,
        Instant updatedAt
) {}
