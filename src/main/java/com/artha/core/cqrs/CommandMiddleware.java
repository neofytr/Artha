package com.artha.core.cqrs;

/**
 * Cross-cutting concern that wraps command execution.
 * Examples: logging, metrics, idempotency, retries, circuit breaking.
 */
public interface CommandMiddleware {
    <R> R invoke(Command<R> command, CommandHandler<Command<R>, R> next);
}
