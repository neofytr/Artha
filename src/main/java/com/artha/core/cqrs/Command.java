package com.artha.core.cqrs;

/**
 * Marker interface for commands.
 * <p>
 * A command represents an intent to change state. It's named in the imperative
 * ({@code OpenAccount}, {@code Deposit}), has a single logical handler, and
 * MAY be rejected based on business rules. Commands are distinct from events,
 * which represent facts that have already happened and cannot be refused.
 */
public interface Command<R> {
}
