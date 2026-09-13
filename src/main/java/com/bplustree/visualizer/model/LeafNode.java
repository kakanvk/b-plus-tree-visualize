package com.bplustree.visualizer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Leaf node containing the keys stored by the B+ tree. */
public final class LeafNode extends BPlusNode {
    final List<Integer> keys = new ArrayList<>();
    private final List<Integer> keysView = Collections.unmodifiableList(keys);
    LeafNode previous;
    LeafNode next;

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public List<Integer> getKeys() {
        return keysView;
    }

    public LeafNode getPrevious() {
        return previous;
    }

    public LeafNode getNext() {
        return next;
    }
}
