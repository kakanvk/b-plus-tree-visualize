package com.bplustree.visualizer.event;

import java.util.List;
import java.util.Objects;

/** Immutable structural and key-level target of an animation event. */
public record EventTarget(
        Long nodeId,
        List<Long> relatedNodeIds,
        Integer childIndex,
        Integer keyIndex,
        Integer focusKey,
        List<Integer> relatedKeys) {

    private static final EventTarget NONE = new EventTarget(
            null, List.of(), null, null, null, List.of());

    public EventTarget {
        relatedNodeIds = relatedNodeIds == null ? List.of() : List.copyOf(relatedNodeIds);
        relatedKeys = relatedKeys == null ? List.of() : List.copyOf(relatedKeys);
    }

    public static EventTarget none() {
        return NONE;
    }

    public static EventTarget node(long nodeId) {
        return new EventTarget(nodeId, List.of(), null, null, null, List.of());
    }

    public static EventTarget node(long nodeId, Integer focusKey, List<Integer> relatedKeys) {
        return new EventTarget(nodeId, List.of(), null, null, focusKey, relatedKeys);
    }

    public static EventTarget key(long nodeId, int keyIndex, int key) {
        return new EventTarget(nodeId, List.of(), null, keyIndex, key, List.of(key));
    }

    public static EventTarget edge(long fromNodeId, long toNodeId, int childIndex) {
        return new EventTarget(fromNodeId, List.of(toNodeId), childIndex, null, null, List.of());
    }

    public static EventTarget nodes(long nodeId, List<Long> relatedNodeIds) {
        return new EventTarget(nodeId, relatedNodeIds, null, null, null, List.of());
    }

    public EventTarget withKeys(Integer focusKey, List<Integer> relatedKeys) {
        return new EventTarget(nodeId, relatedNodeIds, childIndex, keyIndex,
                focusKey, Objects.requireNonNullElse(relatedKeys, List.of()));
    }
}
