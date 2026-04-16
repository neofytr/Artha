package com.artha.core.resilience;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token-bucket rate limiter.
 * <p>
 * Each key (API key, user id, etc.) gets its own bucket that refills at
 * {@code refillRatePerSecond} tokens per second up to {@code capacity}.
 * A request consumes one token; if the bucket is empty, the request is rejected.
 * <p>
 * This implementation is lock-free per-bucket: we encode (tokens, lastRefillNanos)
 * in a single {@code long} and update it via CAS. Two 32-bit halves: high half
 * holds token count (scaled by 1000 for fixed-point refill), low half holds
 * the last refill time in "ticks" relative to a base nanoseconds value.
 * <p>
 * For simplicity and correctness under mixed workloads, we use two AtomicLongs
 * per bucket (tokens scaled by 1000, lastRefillNanos). A more aggressive
 * implementation would pack them into one AtomicLong and CAS.
 */
public class TokenBucketRateLimiter {

    private static final long SCALE = 1_000L;

    private final long capacityScaled;
    private final long refillPerNano;  // scaled
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int capacity, int refillRatePerSecond) {
        this(capacity, refillRatePerSecond, Clock.systemUTC());
    }

    public TokenBucketRateLimiter(int capacity, int refillRatePerSecond, Clock clock) {
        if (capacity <= 0 || refillRatePerSecond <= 0) {
            throw new IllegalArgumentException("capacity and refill rate must be positive");
        }
        this.capacityScaled = capacity * SCALE;
        // tokens per nanosecond (scaled): refillRatePerSecond * SCALE / 1e9
        // store as: numerator = refillRatePerSecond * SCALE, denominator = 1_000_000_000
        // To avoid floating point and preserve precision, compute per tryAcquire call.
        this.refillPerNano = (long) refillRatePerSecond * SCALE;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    public boolean tryAcquire(String key, int permits) {
        if (permits <= 0) throw new IllegalArgumentException("permits must be positive");
        long need = permits * SCALE;

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacityScaled, clock.instant().toEpochMilli() * 1_000_000L));

        // Refill + acquire in a CAS loop.
        while (true) {
            long nowNanos = clock.instant().toEpochMilli() * 1_000_000L + (clock.instant().getNano() % 1_000_000);
            long lastRefill = bucket.lastRefillNanos.get();
            long elapsed = Math.max(0, nowNanos - lastRefill);

            // refilled tokens (scaled): elapsed * refillPerNano / 1e9
            long refilled = (elapsed * refillPerNano) / 1_000_000_000L;
            long currentTokens = bucket.tokensScaled.get();
            long newTokens = Math.min(capacityScaled, currentTokens + refilled);

            if (newTokens < need) {
                // Not enough even after refill; still update lastRefill + tokens so future callers see the latest.
                bucket.tokensScaled.set(newTokens);
                bucket.lastRefillNanos.set(nowNanos);
                return false;
            }

            // Try to atomically consume permits from newTokens.
            if (bucket.tokensScaled.compareAndSet(currentTokens, newTokens - need)) {
                bucket.lastRefillNanos.set(nowNanos);
                return true;
            }
            // CAS lost: retry
        }
    }

    public long currentTokens(String key) {
        Bucket b = buckets.get(key);
        return b == null ? capacityScaled / SCALE : b.tokensScaled.get() / SCALE;
    }

    private static final class Bucket {
        final AtomicLong tokensScaled;
        final AtomicLong lastRefillNanos;

        Bucket(long initialTokens, long nowNanos) {
            this.tokensScaled = new AtomicLong(initialTokens);
            this.lastRefillNanos = new AtomicLong(nowNanos);
        }
    }
}
