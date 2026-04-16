package com.artha.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Retry policy with exponential backoff and decorrelated jitter.
 * <p>
 * Decorrelated jitter (per AWS Architecture Blog) prevents thundering herds better
 * than plain exponential or full jitter: each next delay is picked uniformly from
 * [baseDelay, lastDelay * multiplier], capped at maxDelay.
 * <p>
 * Only retries on exception types that the caller explicitly marks as retryable.
 * Never retries on programmer errors (NPE, IllegalArgument, etc).
 */
public class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final Set<Class<? extends Throwable>> retryableExceptions;

    public RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay, double multiplier,
                       Set<Class<? extends Throwable>> retryableExceptions) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts >= 1");
        if (multiplier < 1.0) throw new IllegalArgumentException("multiplier >= 1.0");
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
        this.retryableExceptions = Set.copyOf(retryableExceptions);
    }

    public <T> T execute(Supplier<T> action) {
        long lastDelayMillis = baseDelay.toMillis();
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (!isRetryable(e) || attempt == maxAttempts) {
                    throw e;
                }
                lastError = e;
                long nextDelay = nextDelay(lastDelayMillis);
                log.warn("Retry attempt {}/{} after {}ms: {}", attempt, maxAttempts, nextDelay, e.getMessage());
                sleep(nextDelay);
                lastDelayMillis = nextDelay;
            }
        }
        // Should not reach here, but for compiler:
        throw lastError != null ? lastError : new IllegalStateException("retry exhausted");
    }

    private boolean isRetryable(Throwable t) {
        for (Class<? extends Throwable> clazz : retryableExceptions) {
            if (clazz.isInstance(t)) return true;
        }
        return false;
    }

    private long nextDelay(long lastDelayMillis) {
        // decorrelated jitter: uniform in [baseDelay, lastDelay * multiplier]
        long upperBound = Math.min((long) (lastDelayMillis * multiplier), maxDelay.toMillis());
        long lower = baseDelay.toMillis();
        if (upperBound <= lower) return lower;
        return ThreadLocalRandom.current().nextLong(lower, upperBound + 1);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry backoff", e);
        }
    }
}
