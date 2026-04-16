package com.artha.core.saga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds an ordered list of saga steps. The definition is immutable once built
 * — new sagas should be defined as static constants.
 */
public class SagaDefinition<C> {

    private final String name;
    private final List<SagaStep<C>> steps;

    private SagaDefinition(String name, List<SagaStep<C>> steps) {
        this.name = name;
        this.steps = List.copyOf(steps);
    }

    public String name() { return name; }
    public List<SagaStep<C>> steps() { return steps; }

    public static <C> Builder<C> named(String name) {
        return new Builder<>(name);
    }

    public static final class Builder<C> {
        private final String name;
        private final List<SagaStep<C>> steps = new ArrayList<>();

        private Builder(String name) { this.name = name; }

        public Builder<C> step(SagaStep<C> step) {
            steps.add(step);
            return this;
        }

        public SagaDefinition<C> build() {
            if (steps.isEmpty()) throw new IllegalStateException("Saga must have at least one step");
            return new SagaDefinition<>(name, Collections.unmodifiableList(steps));
        }
    }
}
