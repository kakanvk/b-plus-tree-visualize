package com.bplustree.visualizer.model;

import java.util.List;
import java.util.Objects;

/** Immutable state of one B+ tree node at an operation checkpoint. */
public record NodeSnapshot(
        long id,
        boolean leaf,
        List<Integer> keys,
        List<Long> childIds,
        Long parentId,
        Long previousLeafId,
        Long nextLeafId) {

    public NodeSnapshot {
        if (id < 0) {
            throw new IllegalArgumentException("id cannot be negative");
        }
        keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
        childIds = List.copyOf(Objects.requireNonNull(childIds, "childIds"));
        if (leaf && !childIds.isEmpty()) {
            throw new IllegalArgumentException("A leaf snapshot cannot have children");
        }
        if (!leaf && (previousLeafId != null || nextLeafId != null)) {
            throw new IllegalArgumentException("Only leaf snapshots can have leaf links");
        }
    }

    public int keyCount() {
        return keys.size();
    }

    public int childCount() {
        return childIds.size();
    }
}
