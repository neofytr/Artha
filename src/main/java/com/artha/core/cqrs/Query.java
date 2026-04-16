package com.artha.core.cqrs;

/**
 * Marker interface for queries. A query reads state and MUST NOT cause side effects.
 * Queries are served from the read model — projections that may be eventually consistent
 * with the write model.
 */
public interface Query<R> {
}
