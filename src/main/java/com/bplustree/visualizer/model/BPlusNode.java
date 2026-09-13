package com.bplustree.visualizer.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Base type for nodes in a B+ tree. */
public abstract class BPlusNode {
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private final long id = NEXT_ID.getAndIncrement();
    InternalNode parent;

    public abstract boolean isLeaf();

    /** Returns an unmodifiable, live view of the node's keys. */
    public abstract List<Integer> getKeys();

    /** Stable for this node's lifetime, including every checkpoint in an operation. */
    public final long getId() {
        return id;
    }

    public final InternalNode getParent() {
        return parent;
    }

    public final int keyCount() {
        return getKeys().size();
    }
}
