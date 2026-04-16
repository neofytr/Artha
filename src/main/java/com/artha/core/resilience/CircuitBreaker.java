package com.artha.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Circuit breaker with a three-state state machine.
 * <p>
 * <b>CLOSED</b>: calls pass through. Failures are counted within a rolling window.
 * When failures cross a threshold, transition to OPEN.<br>
 * <b>OPEN</b>: calls fail fast with {@link CircuitOpenException}. After a cooldown,
 * transition to HALF_OPEN.<br>
 * <b>HALF_OPEN</b>: allow a limited number of probe calls. If they succeed,
 * transition back to CLOSED. Any failure reopens the circuit.
 * <p>
 * Thread-safety: state transitions use CAS so concurrent callers see a consistent
 * view without a mutex on the hot path.
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;
    private final Duration openDuration;
    private final int halfOpenProbes;
    private final Clock clock;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger halfOpenInFlight = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);
    private final AtomicLong openedAtMillis = new AtomicLong(0);

    public CircuitBreaker(String name, int failureThreshold, Duration openDuration, int halfOpenProbes) {
        this(name, failureThreshold, openDuration, halfOpenProbes, Clock.systemUTC());
    }

    public CircuitBreaker(String name, int failureThreshold, Duration openDuration, int halfOpenProbes, Clock clock) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.halfOpenProbes = halfOpenProbes;
        this.clock = clock;
    }

    public <T> T execute(Supplier<T> action) {
        State current = currentState();
        if (current == State.OPEN) {
            throw new CircuitOpenException(name);
        }
        if (current == State.HALF_OPEN) {
            // Limit concurrent probes. Over-subscription is rejected fast.
            int inFlight = halfOpenInFlight.incrementAndGet();
            if (inFlight > halfOpenProbes) {
                halfOpenInFlight.decrementAndGet();
                throw new CircuitOpenException(name);
            }
        }

        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            onFailure();
            throw e;
        } finally {
            if (current == State.HALF_OPEN) {
                halfOpenInFlight.decrementAndGet();
            }
        }
    }

    private State currentState() {
        State s = state.get();
        if (s == State.OPEN) {
            long openedAt = openedAtMillis.get();
            if (clock.millis() - openedAt >= openDuration.toMillis()) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenInFlight.set(0);
                    halfOpenSuccesses.set(0);
                    log.info("Circuit {} transitioning OPEN -> HALF_OPEN", name);
                }
                return State.HALF_OPEN;
            }
        }
        return state.get();
    }

    private void onSuccess() {
        State s = state.get();
        if (s == State.HALF_OPEN) {
            int successes = halfOpenSuccesses.incrementAndGet();
            if (successes >= halfOpenProbes) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    consecutiveFailures.set(0);
                    log.info("Circuit {} recovered: HALF_OPEN -> CLOSED", name);
                }
            }
        } else {
            consecutiveFailures.set(0);
        }
    }

    private void onFailure() {
        State s = state.get();
        if (s == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openedAtMillis.set(clock.millis());
                log.warn("Circuit {} probe failed: HALF_OPEN -> OPEN", name);
            }
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
            openedAtMillis.set(clock.millis());
            log.warn("Circuit {} tripped: CLOSED -> OPEN (failures={})", name, failures);
        }
    }

    public State getState() {
        return currentState();
    }

    public String getName() {
        return name;
    }

    public static class CircuitOpenException extends RuntimeException {
        public CircuitOpenException(String name) {
            super("Circuit breaker '" + name + "' is OPEN");
        }
    }
}
