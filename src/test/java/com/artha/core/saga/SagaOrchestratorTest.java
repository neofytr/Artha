package com.artha.core.saga;

import com.artha.core.resilience.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SagaOrchestratorTest {

    static class Ctx {
        AtomicInteger step1Executed = new AtomicInteger();
        AtomicInteger step1Compensated = new AtomicInteger();
        AtomicInteger step2Executed = new AtomicInteger();
        AtomicInteger step2Compensated = new AtomicInteger();
        AtomicInteger step3Executed = new AtomicInteger();
        boolean step2ShouldFail;
    }

    private SagaStep<Ctx> step(String name, java.util.function.Consumer<Ctx> exec, java.util.function.Consumer<Ctx> comp) {
        return new SagaStep<>() {
            @Override public String name() { return name; }
            @Override public void execute(Ctx ctx) { exec.accept(ctx); }
            @Override public void compensate(Ctx ctx) { comp.accept(ctx); }
        };
    }

    @Test
    void happyPathExecutesAllStepsInOrder() {
        Ctx ctx = new Ctx();
        SagaDefinition<Ctx> def = SagaDefinition.<Ctx>named("test")
                .step(step("s1", c -> c.step1Executed.incrementAndGet(), c -> c.step1Compensated.incrementAndGet()))
                .step(step("s2", c -> c.step2Executed.incrementAndGet(), c -> c.step2Compensated.incrementAndGet()))
                .step(step("s3", c -> c.step3Executed.incrementAndGet(), c -> {}))
                .build();

        SagaOrchestrator<Ctx> orch = new SagaOrchestrator<>(def, new InMemorySagaStateStore<>(), noRetry());
        var result = orch.run(ctx);

        assertThat(result.state()).isEqualTo(SagaState.COMPLETED);
        assertThat(ctx.step1Executed.get()).isEqualTo(1);
        assertThat(ctx.step2Executed.get()).isEqualTo(1);
        assertThat(ctx.step3Executed.get()).isEqualTo(1);
        assertThat(ctx.step1Compensated.get()).isZero();
    }

    @Test
    void failureCompensatesInReverseOrder() {
        Ctx ctx = new Ctx();
        ctx.step2ShouldFail = true;
        SagaDefinition<Ctx> def = SagaDefinition.<Ctx>named("test")
                .step(step("s1", c -> c.step1Executed.incrementAndGet(), c -> c.step1Compensated.incrementAndGet()))
                .step(step("s2", c -> {
                    c.step2Executed.incrementAndGet();
                    if (c.step2ShouldFail) throw new RuntimeException("s2 fail");
                }, c -> c.step2Compensated.incrementAndGet()))
                .step(step("s3", c -> c.step3Executed.incrementAndGet(), c -> {}))
                .build();

        SagaOrchestrator<Ctx> orch = new SagaOrchestrator<>(def, new InMemorySagaStateStore<>(), noRetry());
        var result = orch.run(ctx);

        assertThat(result.state()).isEqualTo(SagaState.FAILED);
        assertThat(ctx.step1Executed.get()).isEqualTo(1);
        assertThat(ctx.step2Executed.get()).isEqualTo(1);
        assertThat(ctx.step3Executed.get()).isZero();
        // Only step1 was fully successful, so only step1 compensates.
        // Step 2 failed mid-execution; its compensation is not run for the failing step itself
        // (orchestrator convention: compensate only steps that completed successfully).
        assertThat(ctx.step1Compensated.get()).isEqualTo(1);
        assertThat(ctx.step2Compensated.get()).isZero();
    }

    @Test
    void stuckWhenCompensationThrows() {
        Ctx ctx = new Ctx();
        SagaDefinition<Ctx> def = SagaDefinition.<Ctx>named("test")
                .step(step("s1", c -> c.step1Executed.incrementAndGet(),
                        c -> { throw new RuntimeException("comp failed"); }))
                .step(step("s2", c -> { throw new RuntimeException("s2 fail"); }, c -> {}))
                .build();

        SagaOrchestrator<Ctx> orch = new SagaOrchestrator<>(def, new InMemorySagaStateStore<>(), noRetry());
        var result = orch.run(ctx);

        assertThat(result.state()).isEqualTo(SagaState.STUCK);
    }

    private RetryPolicy noRetry() {
        return new RetryPolicy(1, Duration.ofMillis(1), Duration.ofMillis(1), 1.0, Set.of());
    }
}
