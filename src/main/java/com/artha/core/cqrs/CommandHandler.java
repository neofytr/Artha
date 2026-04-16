package com.artha.core.cqrs;

/**
 * Handles a specific command type. Each command has exactly one handler.
 */
@FunctionalInterface
public interface CommandHandler<C extends Command<R>, R> {
    R handle(C command);
}
