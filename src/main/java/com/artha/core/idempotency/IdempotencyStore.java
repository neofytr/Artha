package com.artha.core.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Stores (key, response) pairs with a TTL so duplicate commands replay the same result.
 * The store MUST provide atomic "register or return existing" semantics to handle
 * concurrent retries safely.
 */
public interface IdempotencyStore {

    /**
     * Try to reserve a key. If the key already exists, returns the stored response (if completed)
     * or {@link State#IN_PROGRESS}. If it doesn't exist, atomically marks it IN_PROGRESS and returns empty.
     */
    Reservation reserve(IdempotencyKey key, Duration ttl);

    /** Record the final response for a previously-reserved key. */
    void complete(IdempotencyKey key, byte[] responseBody);

    /** Remove a reservation (e.g. on error, so the client can retry). */
    void release(IdempotencyKey key);

    enum State { NEW, IN_PROGRESS, COMPLETED }

    record Reservation(State state, Optional<byte[]> cachedResponse) {
        public static Reservation newReservation() { return new Reservation(State.NEW, Optional.empty()); }
        public static Reservation inProgress() { return new Reservation(State.IN_PROGRESS, Optional.empty()); }
        public static Reservation completed(byte[] response) { return new Reservation(State.COMPLETED, Optional.of(response)); }
    }
}
