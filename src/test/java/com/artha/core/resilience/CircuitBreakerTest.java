package com.artha.core.resilience;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

    private static class MutableClock extends Clock {
        final AtomicLong nowMillis = new AtomicLong(0L);

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(nowMillis.get()); }
        @Override public long millis() { return nowMillis.get(); }
        void advance(Duration d) { nowMillis.addAndGet(d.toMillis()); }
    }

    @Test
    void opensAfterConsecutiveFailures() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, Duration.ofSeconds(10), 1);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("boom"); }))
                    .isInstanceOf(RuntimeException.class);
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> cb.execute(() -> "ok"))
                .isInstanceOf(CircuitBreaker.CircuitOpenException.class);
    }

    @Test
    void successResetsFailureCount() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, Duration.ofSeconds(10), 1);
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("boom"); })).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("boom"); })).isInstanceOf(RuntimeException.class);
        cb.execute(() -> "ok");
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("boom"); })).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("boom"); })).isInstanceOf(RuntimeException.class);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void transitionsToHalfOpenAfterCooldown() {
        MutableClock clock = new MutableClock();
        CircuitBreaker cb = new CircuitBreaker("test", 2, Duration.ofSeconds(5), 1, clock);

        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("boom"); })).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("boom"); })).isInstanceOf(RuntimeException.class);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        clock.advance(Duration.ofSeconds(6));
        // First call after cooldown should be a probe (HALF_OPEN)
        String result = cb.execute(() -> "probe-ok");
        assertThat(result).isEqualTo("probe-ok");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probeFailureReopens() {
        MutableClock clock = new MutableClock();
        CircuitBreaker cb = new CircuitBreaker("test", 2, Duration.ofSeconds(5), 1, clock);

        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("boom"); })).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("boom"); })).isInstanceOf(RuntimeException.class);

        clock.advance(Duration.ofSeconds(6));
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("still broken"); }))
                .isInstanceOf(RuntimeException.class);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
