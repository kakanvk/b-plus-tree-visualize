package com.bplustree.visualizer.event;

import java.util.List;
import java.util.Objects;

/** Immutable outcome and animation plan for a tree operation. */
public record OperationResult(
        boolean success,
        String message,
        List<TreeAnimationEvent> events,
        List<Integer> values,
        int comparisons) {

    public OperationResult {
        message = Objects.requireNonNull(message, "message");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        if (comparisons < 0) {
            throw new IllegalArgumentException("comparisons cannot be negative");
        }
    }
}
