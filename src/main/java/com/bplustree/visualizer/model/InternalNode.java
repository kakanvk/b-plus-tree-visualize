package com.bplustree.visualizer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Internal node whose keys separate its child subtrees. */
public final class InternalNode extends BPlusNode {
    final List<Integer> keys = new ArrayList<>();
    final List<BPlusNode> children = new ArrayList<>();
    private final List<Integer> keysView = Collections.unmodifiableList(keys);
    private final List<BPlusNode> childrenView = Collections.unmodifiableList(children);

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public List<Integer> getKeys() {
        return keysView;
    }

    /** Returns an unmodifiable, live view of this node's children. */
    public List<BPlusNode> getChildren() {
        return childrenView;
    }
}
