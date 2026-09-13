package com.bplustree.visualizer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class BPlusTreeTest {
    @Test
    void validatesOrderBounds() {
        assertThrows(IllegalArgumentException.class, () -> new BPlusTree(2));
        assertThrows(IllegalArgumentException.class, () -> new BPlusTree(9));
        assertEquals(3, new BPlusTree(3).getOrder());
        assertEquals(8, new BPlusTree(8).getOrder());
    }

    @Test
    void insertsAndSearchesUniqueKeys() {
        BPlusTree tree = new BPlusTree(4);

        assertTrue(tree.insert(40));
        assertTrue(tree.insert(10));
        assertTrue(tree.insert(30));
        assertTrue(tree.insert(20));
        assertFalse(tree.insert(20));

        assertEquals(List.of(10, 20, 30, 40), tree.keys());
        assertEquals(4, tree.size());
        assertTrue(tree.search(30));
        assertTrue(tree.contains(10));
        assertFalse(tree.search(99));
        assertValid(tree);
    }

    @Test
    void performsLeafAndInternalSplitsForEverySupportedOrder() {
        for (int order = BPlusTree.MIN_ORDER; order <= BPlusTree.MAX_ORDER; order++) {
            BPlusTree tree = new BPlusTree(order);
            for (int key = 1; key <= 200; key++) {
                assertTrue(tree.insert(key));
                assertValid(tree);
            }

            assertTrue(tree.height() >= 3, "Expected internal splits for order " + order);
            assertEquals(range(1, 200), tree.keys());
            assertNodeCapacities(tree.getRoot(), order);
        }
    }

    @Test
    void deletesWithoutUnderflow() {
        BPlusTree tree = treeOf(4, 1, 2, 3, 4, 5);
        int leavesBefore = tree.statistics().leafNodeCount();

        assertTrue(tree.delete(5));

        assertEquals(List.of(1, 2, 3, 4), tree.keys());
        assertEquals(leavesBefore, tree.statistics().leafNodeCount());
        assertFalse(tree.delete(99));
        assertValid(tree);
    }

    @Test
    void borrowsFromLeftLeafAndUpdatesSeparator() {
        BPlusTree tree = new BPlusTree(5);
        tree.insert(1); tree.insert(3); tree.insert(5); tree.insert(7); tree.insert(9);
        tree.insert(2); tree.insert(4); tree.insert(6); tree.insert(8); tree.insert(10);
        // Tree: Root [5,7], leaves: [1,2,3,4], [5,6], [7,8,9,10]

        assertTrue(tree.delete(6));

        InternalNode root = assertInstanceOf(InternalNode.class, tree.getRoot());
        assertEquals(List.of(4, 7), root.getKeys());
        assertEquals(List.of(4, 5), root.getChildren().get(1).getKeys());
        assertEquals(List.of(7, 8, 9, 10), root.getChildren().get(2).getKeys());
        assertValid(tree);
    }

    @Test
    void borrowsFromRightLeafAndUpdatesSeparator() {
        BPlusTree tree = treeOf(4, 1, 2, 3, 4, 5, 6, 7);

        assertTrue(tree.delete(3));

        InternalNode root = assertInstanceOf(InternalNode.class, tree.getRoot());
        assertEquals(List.of(4, 6), root.getKeys());
        assertEquals(List.of(4, 5), root.getChildren().get(1).getKeys());
        assertEquals(List.of(6, 7), root.getChildren().get(2).getKeys());
        assertValid(tree);
    }

    @Test
    void mergesLeavesWhenNeitherSiblingCanLend() {
        BPlusTree tree = treeOf(3, 1, 2, 3, 4);
        int leavesBefore = tree.statistics().leafNodeCount();

        assertTrue(tree.delete(1));

        assertEquals(leavesBefore - 1, tree.statistics().leafNodeCount());
        assertEquals(List.of(2, 3, 4), tree.keys());
        assertValid(tree);
    }

    @Test
    void shrinksRootAfterMerge() {
        BPlusTree tree = treeOf(3, 1, 2, 3, 4);
        assertInstanceOf(InternalNode.class, tree.getRoot());

        assertTrue(tree.delete(3));
        assertTrue(tree.delete(2));
        assertTrue(tree.delete(1));

        LeafNode root = assertInstanceOf(LeafNode.class, tree.getRoot());
        assertEquals(List.of(4), root.getKeys());
        assertNull(root.getParent());
        assertEquals(1, tree.height());
        assertValid(tree);
    }

    @Test
    void enforcesMinimumLeafOccupancyForEvenOrders() {
        for (int order : List.of(4, 6, 8)) {
            BPlusTree tree = new BPlusTree(order);
            tree.insertAll(range(1, order * 8));
            int minimumLeafKeys = order / 2;
            LeafNode leaf = leftmostLeaf(tree);
            while (leaf != null) {
                assertTrue(leaf == tree.getRoot() || leaf.keyCount() >= minimumLeafKeys,
                        "Order " + order + " has underfilled leaf " + leaf.getKeys());
                leaf = leaf.getNext();
            }
            assertValid(tree);
        }
    }

    @Test
    void maintainsBidirectionalLeafLinks() {
        BPlusTree tree = new BPlusTree(3);
        for (int key = 0; key < 40; key++) {
            tree.insert(key);
        }

        LeafNode leaf = leftmostLeaf(tree);
        LeafNode previous = null;
        List<Integer> forward = new ArrayList<>();
        while (leaf != null) {
            assertEquals(previous, leaf.getPrevious());
            forward.addAll(leaf.getKeys());
            previous = leaf;
            leaf = leaf.getNext();
        }

        List<Integer> backward = new ArrayList<>();
        while (previous != null) {
            List<Integer> reversedLeaf = new ArrayList<>(previous.getKeys());
            Collections.reverse(reversedLeaf);
            backward.addAll(reversedLeaf);
            previous = previous.getPrevious();
        }
        List<Integer> expectedBackward = new ArrayList<>(forward);
        Collections.reverse(expectedBackward);
        assertEquals(expectedBackward, backward);
        assertValid(tree);
    }

    @Test
    void performsInclusiveRangeSearchAcrossLeaves() {
        BPlusTree tree = new BPlusTree(3);
        tree.insertAll(List.of(50, 10, 70, 20, 60, 30, 40, 80));

        assertEquals(List.of(20, 30, 40, 50, 60), tree.rangeSearch(20, 60));
        assertEquals(List.of(10), tree.rangeSearch(1, 10));
        assertEquals(List.of(), tree.rangeSearch(35, 35));
        assertEquals(List.of(), tree.rangeSearch(90, 10));
        assertEquals(tree.keys(), tree.rangeSearch(Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void reportsStatisticsAndResetsCleanly() {
        BPlusTree tree = new BPlusTree(5);
        tree.insertAll(range(1, 50));

        BPlusTreeStatistics statistics = tree.statistics();
        assertEquals(5, statistics.order());
        assertEquals(50, statistics.keyCount());
        assertEquals(Integer.valueOf(1), statistics.minimumKey());
        assertEquals(Integer.valueOf(50), statistics.maximumKey());
        assertEquals(
                statistics.internalNodeCount() + statistics.leafNodeCount(),
                statistics.nodeCount());
        assertTrue(statistics.averageLeafFillRatio() > 0.0);
        assertTrue(statistics.averageLeafFillRatio() <= 1.0);

        tree.clear();
        assertTrue(tree.isEmpty());
        assertEquals(List.of(), tree.keys());
        assertEquals(1, tree.statistics().height());
        assertNull(tree.statistics().minimumKey());
        assertValid(tree);
    }

    @Test
    void staysConsistentThroughLongInsertDeleteSequence() {
        BPlusTree tree = new BPlusTree(3);
        for (int key = 1; key <= 300; key++) {
            tree.insert(key);
        }
        for (int key = 1; key <= 300; key++) {
            assertTrue(tree.delete(key));
            assertValid(tree);
        }

        assertTrue(tree.isEmpty());
        assertInstanceOf(LeafNode.class, tree.getRoot());
        assertEquals(1, tree.height());
    }

    @Test
    void matchesTreeSetUnderRandomizedOperationsForEveryOrder() {
        for (int order = BPlusTree.MIN_ORDER; order <= BPlusTree.MAX_ORDER; order++) {
            BPlusTree tree = new BPlusTree(order);
            TreeSet<Integer> expected = new TreeSet<>();
            Random random = new Random(7_311L + order);

            for (int operation = 0; operation < 2_000; operation++) {
                int key = random.nextInt(500) - 250;
                if (random.nextBoolean()) {
                    assertEquals(expected.add(key), tree.insert(key));
                } else {
                    assertEquals(expected.remove(key), tree.delete(key));
                }

                assertEquals(new ArrayList<>(expected), tree.keys());
                assertEquals(expected.contains(key), tree.search(key));
                assertEquals(expected.size(), tree.size());
                assertValid(tree);
            }
        }
    }

    private static BPlusTree treeOf(int order, int... keys) {
        BPlusTree tree = new BPlusTree(order);
        for (int key : keys) {
            tree.insert(key);
        }
        assertValid(tree);
        return tree;
    }

    private static LeafNode leftmostLeaf(BPlusTree tree) {
        BPlusNode node = tree.getRoot();
        while (node instanceof InternalNode internal) {
            node = internal.getChildren().get(0);
        }
        return (LeafNode) node;
    }

    private static List<Integer> range(int start, int end) {
        List<Integer> result = new ArrayList<>();
        for (int value = start; value <= end; value++) {
            result.add(value);
        }
        return result;
    }

    private static void assertNodeCapacities(BPlusNode node, int order) {
        if (node instanceof LeafNode leaf) {
            assertTrue(leaf.keyCount() <= order - 1);
            return;
        }
        InternalNode internal = (InternalNode) node;
        assertTrue(internal.getChildren().size() <= order);
        assertEquals(internal.getChildren().size() - 1, internal.keyCount());
        internal.getChildren().forEach(child -> assertNodeCapacities(child, order));
    }

    private static void assertValid(BPlusTree tree) {
        ValidationResult result = tree.validationResult();
        assertTrue(result.valid(), () -> String.join("; ", result.errors()));
        tree.assertValid();
    }
}
