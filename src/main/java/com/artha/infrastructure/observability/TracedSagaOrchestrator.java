package com.artha.infrastructure.observability;

import com.artha.core.resilience.RetryPolicy;
import com.artha.core.saga.SagaDefinition;
import com.artha.core.saga.SagaOrchestrator;
import com.artha.core.saga.SagaStateStore;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.UUID;

/**
 * Wraps {@link SagaOrchestrator} with manual tracing. Each saga run becomes a span;
 * failures and the final state are recorded as span attributes. Because step
 * execution nests inside this span, any downstream spans (JDBC, Kafka producer) are
 * automatically children — you get one coherent trace per logical transfer.
 */
public class TracedSagaOrchestrator<C> extends SagaOrchestrator<C> {

    private final Tracer tracer;
    private final String sagaName;

    public TracedSagaOrchestrator(SagaDefinition<C> definition,
                                  SagaStateStore<C> stateStore,
                                  RetryPolicy retry,
                                  Tracer tracer) {
        super(definition, stateStore, retry);
        this.tracer = tracer;
        this.sagaName = definition.name();
    }

    @Override
    public SagaResult run(UUID sagaId, C context) {
        Span span = tracer.nextSpan().name("saga." + sagaName).start();
        try (var ws = tracer.withSpan(span)) {
            span.tag("saga.id", sagaId.toString());
            SagaResult result = super.run(sagaId, context);
            span.tag("saga.state", result.state().name());
            if (result.error() != null) {
                span.tag("saga.error", result.error());
            }
            return result;
        } finally {
            span.end();
        }
    }
}
