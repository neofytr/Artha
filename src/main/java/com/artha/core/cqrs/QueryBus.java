package com.artha.core.cqrs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QueryBus {

    private final Map<Class<?>, QueryHandler<?, ?>> handlers = new ConcurrentHashMap<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <R> R dispatch(Query<R> query) {
        QueryHandler handler = handlers.get(query.getClass());
        if (handler == null) {
            throw new IllegalStateException("No query handler registered for " + query.getClass().getName());
        }
        return (R) handler.handle(query);
    }

    public <Q extends Query<R>, R> void register(Class<Q> queryType, QueryHandler<Q, R> handler) {
        if (handlers.putIfAbsent(queryType, handler) != null) {
            throw new IllegalStateException("Query handler already registered for " + queryType.getName());
        }
    }
}
