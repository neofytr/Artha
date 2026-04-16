package com.artha.core.idempotency;

import java.util.Objects;

/**
 * Client-supplied key used to deduplicate retries of the same operation.
 * <p>
 * Callers generate a UUID/ULID per logical operation and send it with every
 * retry. The server stores (key, response) and, on a duplicate, replays the
 * stored response instead of re-executing. This is the mechanism that makes
 * POSTs safe to retry over flaky networks.
 */
public record IdempotencyKey(String value) {
    public IdempotencyKey {
        Objects.requireNonNull(value, "idempotency key value");
        if (value.isBlank()) throw new IllegalArgumentException("idempotency key cannot be blank");
        if (value.length() > 255) throw new IllegalArgumentException("idempotency key too long");
    }
}
