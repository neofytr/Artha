package com.artha.core.cqrs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory command bus with pluggable middleware.
 * <p>
 * Middleware is applied in registration order, onion-style: the first middleware
 * registered is the outermost. Typical ordering: logging → metrics → idempotency
 * → actual handler. This makes it easy to add cross-cutting concerns without
 * touching individual handlers.
 */
public class InMemoryCommandBus implements CommandBus {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCommandBus.class);

    private final Map<Class<?>, CommandHandler<?, ?>> handlers = new ConcurrentHashMap<>();
    private final List<CommandMiddleware> middlewares;

    public InMemoryCommandBus(List<CommandMiddleware> middlewares) {
        this.middlewares = List.copyOf(middlewares);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <R> R dispatch(Command<R> command) {
        Class<?> type = command.getClass();
        CommandHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for " + type.getName());
        }

        // Build the call chain: the innermost is the real handler,
        // each middleware wraps it.
        CommandHandler finalChain = handler;
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            CommandMiddleware mw = middlewares.get(i);
            CommandHandler next = finalChain;
            finalChain = cmd -> mw.invoke((Command) cmd, next);
        }

        log.debug("Dispatching command {}", type.getSimpleName());
        return (R) finalChain.handle(command);
    }

    @Override
    public <C extends Command<R>, R> void register(Class<C> commandType, CommandHandler<C, R> handler) {
        if (handlers.putIfAbsent(commandType, handler) != null) {
            throw new IllegalStateException("Handler already registered for " + commandType.getName());
        }
    }
}
