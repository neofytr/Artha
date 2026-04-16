package com.artha.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionView(
        UUID eventId,
        UUID accountId,
        String kind,
        BigDecimal amount,
        String currency,
        String reference,
        Instant occurredAt
) {}
