package com.bplustree.visualizer.event;

import java.util.List;
import java.util.Objects;

/** Immutable result and direct checkpoint trace of one model operation. */
public record OperationTrace<T>(T result, List<TreeAnimationEvent> events, int comparisons) {
    public OperationTrace {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (comparisons < 0) {
            throw new IllegalArgumentException("comparisons cannot be negative");
        }
    }
}
