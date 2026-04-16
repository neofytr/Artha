package com.artha.core.cqrs;

/**
 * Dispatches commands to their registered handlers.
 * Implementations may add middleware: logging, metrics, idempotency, retries.
 */
public interface CommandBus {
    <R> R dispatch(Command<R> command);

    <C extends Command<R>, R> void register(Class<C> commandType, CommandHandler<C, R> handler);
}
