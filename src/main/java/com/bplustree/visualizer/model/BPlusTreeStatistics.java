package com.bplustree.visualizer.model;

/** Immutable structural statistics for a B+ tree snapshot. */
public record BPlusTreeStatistics(
        int order,
        int height,
        int keyCount,
        int nodeCount,
        int internalNodeCount,
        int leafNodeCount,
        Integer minimumKey,
        Integer maximumKey,
        double averageLeafFillRatio) {
}
