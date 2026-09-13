package com.bplustree.visualizer.event;

import com.bplustree.visualizer.model.TreeSnapshot;
import java.util.List;
import java.util.Objects;

/** An immutable, UI-independent description of one direct tree checkpoint. */
public final class TreeAnimationEvent {
    private final EventType type;
    private final String title;
    private final String detail;
    private final TreeSnapshot snapshot;
    private final EventTarget target;
    private final int comparisons;

    public TreeAnimationEvent(
            EventType type,
            String title,
            String detail,
            TreeSnapshot snapshot,
            EventTarget target,
            int comparisons) {
        this.type = Objects.requireNonNull(type, "type");
        this.title = Objects.requireNonNull(title, "title");
        this.detail = Objects.requireNonNull(detail, "detail");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.target = Objects.requireNonNull(target, "target");
        if (comparisons < 0) {
            throw new IllegalArgumentException("comparisons cannot be negative");
        }
        this.comparisons = comparisons;
    }

    public TreeAnimationEvent(
            EventType type,
            String title,
            String detail,
            TreeSnapshot snapshot,
            EventTarget target) {
        this(type, title, detail, snapshot, target, 0);
    }

    /**
     * Compatibility constructor for callers that still target events by key.
     * New model traces always use the snapshot/target constructor.
     */
    public TreeAnimationEvent(
            EventType type,
            String title,
            String detail,
            Integer focusKey,
            List<Integer> relatedKeys) {
        this(type, title, detail, TreeSnapshot.empty(),
                new EventTarget(null, List.of(), null, null, focusKey, relatedKeys), 0);
    }

    public EventType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    public TreeSnapshot snapshot() {
        return snapshot;
    }

    public EventTarget target() {
        return target;
    }

    /** Number of key comparisons completed at this checkpoint. */
    public int comparisons() {
        return comparisons;
    }

    /** Legacy key-based focus accessor retained for the current renderer. */
    public Integer focusKey() {
        return target.focusKey();
    }

    /** Legacy key-based related accessor retained for the current renderer. */
    public List<Integer> relatedKeys() {
        return target.relatedKeys();
    }

    @Override
    public String toString() {
        return "TreeAnimationEvent[type=" + type + ", title=" + title
                + ", detail=" + detail + ", snapshot=" + snapshot
                + ", target=" + target + ", comparisons=" + comparisons + "]";
    }
}
