package com.artha.core.resilience;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    private static class MutableClock extends Clock {
        final AtomicLong nowMillis = new AtomicLong(1_000_000L);
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(nowMillis.get()); }
        @Override public long millis() { return nowMillis.get(); }
        void advance(Duration d) { nowMillis.addAndGet(d.toMillis()); }
    }

    @Test
    void allowsBurstUpToCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 1);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("user")).isTrue();
        }
        assertThat(limiter.tryAcquire("user")).isFalse();
    }

    @Test
    void refillsOverTime() {
        MutableClock clock = new MutableClock();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 10, clock);
        // burn through
        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire("u")).isTrue();
        assertThat(limiter.tryAcquire("u")).isFalse();

        // 200ms at 10/sec -> 2 tokens
        clock.advance(Duration.ofMillis(200));
        assertThat(limiter.tryAcquire("u")).isTrue();
        assertThat(limiter.tryAcquire("u")).isTrue();
        assertThat(limiter.tryAcquire("u")).isFalse();
    }

    @Test
    void bucketsAreIsolatedPerKey() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 1);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue();
    }
}
