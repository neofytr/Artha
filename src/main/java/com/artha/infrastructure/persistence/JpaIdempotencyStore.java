package com.artha.infrastructure.persistence;

import com.artha.core.idempotency.IdempotencyKey;
import com.artha.core.idempotency.IdempotencyStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Postgres-backed idempotency store. The primary-key constraint on the key
 * value gives us atomic reserve-or-fail: a duplicate INSERT throws and we
 * know someone else got there first.
 */
@Component
public class JpaIdempotencyStore implements IdempotencyStore {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(IdempotencyKey key, Duration ttl) {
        IdempotencyRecord existing = em.find(IdempotencyRecord.class, key.value());
        if (existing != null) {
            if (existing.getExpiresAt().isBefore(Instant.now())) {
                em.remove(existing);
                em.flush();
                // fall through to create
            } else if (existing.getState() == IdempotencyRecord.State.COMPLETED) {
                return Reservation.completed(existing.getResponse());
            } else {
                return Reservation.inProgress();
            }
        }
        try {
            IdempotencyRecord record = new IdempotencyRecord(
                    key.value(), IdempotencyRecord.State.IN_PROGRESS, null, Instant.now().plus(ttl)
            );
            em.persist(record);
            em.flush();
            return Reservation.newReservation();
        } catch (DataIntegrityViolationException race) {
            // Concurrent reserve from another node beat us
            IdempotencyRecord winner = em.find(IdempotencyRecord.class, key.value());
            if (winner != null && winner.getState() == IdempotencyRecord.State.COMPLETED) {
                return Reservation.completed(winner.getResponse());
            }
            return Reservation.inProgress();
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(IdempotencyKey key, byte[] responseBody) {
        IdempotencyRecord record = em.find(IdempotencyRecord.class, key.value());
        if (record != null) {
            record.setState(IdempotencyRecord.State.COMPLETED);
            record.setResponse(responseBody);
            em.merge(record);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(IdempotencyKey key) {
        IdempotencyRecord record = em.find(IdempotencyRecord.class, key.value());
        if (record != null) em.remove(record);
    }

    public Optional<IdempotencyRecord> find(String value) {
        return Optional.ofNullable(em.find(IdempotencyRecord.class, value));
    }
}
