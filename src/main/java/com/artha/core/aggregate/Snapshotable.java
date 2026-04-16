package com.artha.core.aggregate;

/**
 * Aggregates that can serialize their state for faster reloading.
 * A snapshot is a "jump-ahead" optimization: rather than replaying 10,000 events
 * from scratch, we load a snapshot at version N and replay only events after N.
 */
public interface Snapshotable<S> {
    /** Capture a serializable representation of the current state. */
    S captureSnapshot();

    /** Rehydrate state from a snapshot. */
    void restoreFromSnapshot(S snapshot);

    /** Type token for deserialization. */
    Class<S> snapshotType();
}
