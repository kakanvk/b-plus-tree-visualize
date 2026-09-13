package com.bplustree.visualizer.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable B+ tree state captured directly at an animation checkpoint.
 *
 * <p>Snapshots intentionally permit transient overflow, underflow, and a
 * one-child internal root so an operation can expose states that are repaired
 * by its following checkpoints.</p>
 */
public final class TreeSnapshot {
    private static final TreeSnapshot EMPTY = new TreeSnapshot(0, 0, null, List.of());

    private final int order;
    private final int size;
    private final Long rootId;
    private final List<NodeSnapshot> nodes;
    private final Map<Long, NodeSnapshot> nodesById;

    public TreeSnapshot(int order, int size, Long rootId, List<NodeSnapshot> nodes) {
        if (order < 0) {
            throw new IllegalArgumentException("order cannot be negative");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size cannot be negative");
        }
        this.order = order;
        this.size = size;
        this.rootId = rootId;
        this.nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));

        Map<Long, NodeSnapshot> index = new LinkedHashMap<>();
        for (NodeSnapshot node : this.nodes) {
            if (index.put(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate node id " + node.id());
            }
        }
        if (rootId != null && !index.containsKey(rootId)) {
            throw new IllegalArgumentException("rootId does not identify a node");
        }
        this.nodesById = Map.copyOf(index);
    }

    /** Placeholder used only by the legacy event constructor. */
    public static TreeSnapshot empty() {
        return EMPTY;
    }

    public int order() {
        return order;
    }

    public int size() {
        return size;
    }

    public Long rootId() {
        return rootId;
    }

    public List<NodeSnapshot> nodes() {
        return nodes;
    }

    public Optional<NodeSnapshot> root() {
        return rootId == null ? Optional.empty() : Optional.of(nodesById.get(rootId));
    }

    public Optional<NodeSnapshot> node(long id) {
        return Optional.ofNullable(nodesById.get(id));
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TreeSnapshot snapshot)) {
            return false;
        }
        return order == snapshot.order
                && size == snapshot.size
                && Objects.equals(rootId, snapshot.rootId)
                && nodes.equals(snapshot.nodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(order, size, rootId, nodes);
    }

    @Override
    public String toString() {
        return "TreeSnapshot[order=" + order + ", size=" + size
                + ", rootId=" + rootId + ", nodes=" + nodes + "]";
    }
}
