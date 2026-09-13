package com.bplustree.visualizer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bplustree.visualizer.event.EventType;
import com.bplustree.visualizer.event.OperationTrace;
import com.bplustree.visualizer.event.TreeAnimationEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BPlusTreeTraceTest {

    @Test
    void insertionUsesInsertedOverflowFrameAndOneRootStructuralFrame() {
        BPlusTree tree = new BPlusTree(4);
        tree.insertAll(List.of(10, 20, 30));

        OperationTrace<Boolean> trace = tree.insertTraced(25);

        assertTrue(trace.result());
        assertEquals(0, count(trace.events(), EventType.NODE_OVERFLOW));
        assertEquals(0, count(trace.events(), EventType.SPLIT_NODE));
        assertEquals(0, count(trace.events(), EventType.PROMOTE_KEY));
        assertEquals(1, count(trace.events(), EventType.CREATE_ROOT));
        TreeAnimationEvent insertion = first(trace.events(), EventType.INSERT_KEY);
        NodeSnapshot overflowingNode = insertion.snapshot()
                .node(insertion.target().nodeId()).orElseThrow();
        assertEquals(List.of(10, 20, 25, 30), overflowingNode.keys());
        TreeAnimationEvent createRoot = first(trace.events(), EventType.CREATE_ROOT);
        assertNotEquals(insertion.snapshot(), createRoot.snapshot());
        assertTrue(createRoot.detail().contains(
                "separator " + createRoot.focusKey()));
        assertEquals(tree.snapshot(), last(trace.events()).snapshot());
    }

    @Test
    void insertionEmitsExactlyOneStructuralEventPerCascadeLevel() {
        BPlusTree tree = new BPlusTree(3);
        for (int key = 1; key < 9; key++) {
            tree.insert(key);
        }

        OperationTrace<Boolean> trace = tree.insertTraced(9);

        long structuralEvents = count(trace.events(), EventType.SPLIT_NODE)
                + count(trace.events(), EventType.CREATE_ROOT);
        assertEquals(3, structuralEvents);
        assertEquals(0, count(trace.events(), EventType.PROMOTE_KEY));
        assertEquals(0, count(trace.events(), EventType.NODE_OVERFLOW));
        assertConsecutiveStructuralSnapshotsDiffer(trace.events());
        assertEquals(range(1, 9), tree.keys());
        assertTrue(tree.validateInvariants());
    }

    @Test
    void nodeIdsStayStableFromTraversalThroughStructuralCheckpoint() {
        BPlusTree tree = new BPlusTree(4);
        tree.insertAll(List.of(10, 20, 30));
        long originalRootId = tree.getRoot().getId();

        OperationTrace<Boolean> trace = tree.insertTraced(40);

        TreeAnimationEvent visit = first(trace.events(), EventType.VISIT_NODE);
        TreeAnimationEvent insertion = first(trace.events(), EventType.INSERT_KEY);
        TreeAnimationEvent structural = first(trace.events(), EventType.CREATE_ROOT);
        assertEquals(originalRootId, visit.target().nodeId());
        assertEquals(originalRootId, insertion.target().nodeId());
        assertTrue(insertion.snapshot().node(originalRootId).isPresent());
        assertTrue(structural.snapshot().node(originalRootId).isPresent());
    }

    @Test
    void deletionCombinesSeparatorRecomputeWithDeleteAndBorrowFrames() {
        BPlusTree tree = new BPlusTree(5);
        tree.insertAll(List.of(1, 3, 5, 7, 9, 2, 4, 6, 8, 10));

        OperationTrace<Boolean> trace = tree.deleteTraced(6);

        assertOrdered(trace.events(), EventType.MATCH_KEY, EventType.DELETE_KEY,
                EventType.BORROW_KEY, EventType.COMPLETE);
        assertEquals(0, count(trace.events(), EventType.NODE_UNDERFLOW));
        assertEquals(0, count(trace.events(), EventType.UPDATE_SEPARATOR));
        TreeAnimationEvent deletion = first(trace.events(), EventType.DELETE_KEY);
        TreeAnimationEvent borrow = first(trace.events(), EventType.BORROW_KEY);
        assertTrue(deletion.detail().contains("ancestor"));
        assertTrue(borrow.detail().contains("ancestor"));
        assertNotEquals(deletion.snapshot(), borrow.snapshot());
        assertAllSeparatorsCorrect(deletion.snapshot());
        assertAllSeparatorsCorrect(borrow.snapshot());
        assertEquals(List.of(1, 2, 3, 4, 5, 7, 8, 9, 10), tree.keys());
    }

    @Test
    void mergeAndRootShrinkArePreMutationFramesWithLiveTargets() {
        BPlusTree tree = new BPlusTree(3);
        tree.insertAll(range(1, 80));

        OperationTrace<Boolean> trace = tree.deleteTraced(1);

        assertTrue(count(trace.events(), EventType.MERGE_NODE) >= 2);
        assertEquals(0, count(trace.events(), EventType.UPDATE_SEPARATOR));
        for (int index = 0; index < trace.events().size(); index++) {
            TreeAnimationEvent event = trace.events().get(index);
            if (event.type() == EventType.MERGE_NODE) {
                assertTrue(event.snapshot().node(event.target().nodeId()).isPresent());
                assertEquals(2, event.target().relatedNodeIds().size());
                event.target().relatedNodeIds().forEach(id ->
                        assertTrue(event.snapshot().node(id).isPresent()));
                assertTrue(index + 1 < trace.events().size());
                assertNotEquals(event.snapshot(), trace.events().get(index + 1).snapshot());
            }
            if (event.type() == EventType.SHRINK_ROOT) {
                assertEquals(event.target().nodeId(), event.snapshot().rootId());
                assertTrue(event.snapshot().node(event.target().nodeId()).isPresent());
                event.target().relatedNodeIds().forEach(id ->
                        assertTrue(event.snapshot().node(id).isPresent()));
                assertNotEquals(event.snapshot(), trace.events().get(index + 1).snapshot());
            }
        }
        assertEquals(range(2, 80), tree.keys());
        assertEquals(tree.snapshot(), last(trace.events()).snapshot());
        assertTrue(tree.validateInvariants());
    }

    @Test
    void deleteSixteenKeepsEveryAncestorSeparatorVisibleAndCorrect() {
        BPlusTree tree = new BPlusTree(4);
        tree.insertAll(range(1, 40));

        OperationTrace<Boolean> trace = tree.deleteTraced(16);

        TreeAnimationEvent deletion = first(trace.events(), EventType.DELETE_KEY);
        assertTrue(deletion.target().relatedNodeIds().size() >= 2);
        assertTrue(deletion.detail().contains("mọi khóa phân cách ancestor"));
        deletion.target().relatedNodeIds().forEach(id ->
                assertTrue(deletion.snapshot().node(id).isPresent()));
        for (TreeAnimationEvent event : trace.events()) {
            if (event.type() == EventType.BORROW_KEY
                    || event.type() == EventType.NODE_UNDERFLOW
                    || event.type() == EventType.COMPLETE) {
                assertAllSeparatorsCorrect(event.snapshot());
            }
        }
        assertEquals(rangeExcept(1, 40, 16), tree.keys());
        assertEquals(tree.snapshot(), last(trace.events()).snapshot());
    }

    @Test
    void rangeStopsBeforeLeafWhoseMinimumExceedsUpperBound() {
        BPlusTree tree = new BPlusTree(3);
        tree.insertAll(List.of(10, 20, 30, 40, 50, 60));

        OperationTrace<List<Integer>> trace = tree.rangeSearchTraced(20, 20);

        assertEquals(List.of(20), trace.result());
        assertEquals(1, count(trace.events(), EventType.HIGHLIGHT_RANGE));
        assertFalse(trace.events().stream().anyMatch(event ->
                event.type() == EventType.TRAVERSE_EDGE
                        && event.target().childIndex() == null));
        TreeAnimationEvent rangeFrame = first(trace.events(), EventType.HIGHLIGHT_RANGE);
        assertFalse(rangeFrame.target().relatedKeys().contains(30));
        TreeAnimationEvent boundary = trace.events().stream()
                .filter(event -> event.type() == EventType.COMPARE_KEY)
                .filter(event -> event.title().equals("Kiểm tra giới hạn nút lá kế tiếp"))
                .findFirst()
                .orElseThrow();
        assertTrue(boundary.detail().contains("không còn"));
        assertEquals(boundary.comparisons(), trace.comparisons());
    }

    @Test
    void eventsCarryCumulativeComparisonCounts() {
        BPlusTree tree = new BPlusTree(3);
        tree.insertAll(List.of(10, 20, 30, 40, 50, 60));

        OperationTrace<Boolean> trace = tree.searchTraced(50);

        int previous = 0;
        for (TreeAnimationEvent event : trace.events()) {
            assertTrue(event.comparisons() >= previous);
            if (event.type() == EventType.COMPARE_KEY) {
                assertTrue(event.comparisons() > previous);
            }
            previous = event.comparisons();
        }
        assertEquals(trace.comparisons(), last(trace.events()).comparisons());
        TreeAnimationEvent legacy = new TreeAnimationEvent(
                EventType.COMPLETE, "", "", null, List.of());
        assertEquals(0, legacy.comparisons());
    }

    @Test
    void snapshotsAndTargetsAreImmutable() {
        BPlusTree tree = new BPlusTree(4);
        TreeAnimationEvent event = first(tree.insertTraced(10).events(), EventType.INSERT_KEY);

        assertNotNull(event.snapshot());
        assertNotNull(event.target());
        assertThrows(UnsupportedOperationException.class,
                () -> event.snapshot().nodes().add(event.snapshot().nodes().get(0)));
        assertThrows(UnsupportedOperationException.class,
                () -> event.snapshot().nodes().get(0).keys().add(99));
        assertThrows(UnsupportedOperationException.class,
                () -> event.target().relatedNodeIds().add(123L));
    }

    private static void assertConsecutiveStructuralSnapshotsDiffer(
            List<TreeAnimationEvent> events) {
        TreeAnimationEvent previous = null;
        for (TreeAnimationEvent event : events) {
            if (event.type() == EventType.SPLIT_NODE || event.type() == EventType.CREATE_ROOT) {
                if (previous != null) {
                    assertNotEquals(previous.snapshot(), event.snapshot());
                }
                previous = event;
            }
        }
    }

    private static void assertAllSeparatorsCorrect(TreeSnapshot snapshot) {
        for (NodeSnapshot node : snapshot.nodes()) {
            if (node.leaf()) {
                continue;
            }
            List<Integer> expected = node.childIds().stream()
                    .skip(1)
                    .map(id -> subtreeMinimum(snapshot, id))
                    .toList();
            assertEquals(expected, node.keys(), "Incorrect separators at node " + node.id());
        }
    }

    private static int subtreeMinimum(TreeSnapshot snapshot, long nodeId) {
        NodeSnapshot node = snapshot.node(nodeId).orElseThrow();
        while (!node.leaf()) {
            node = snapshot.node(node.childIds().get(0)).orElseThrow();
        }
        return node.keys().get(0);
    }

    private static TreeAnimationEvent first(
            List<TreeAnimationEvent> events, EventType type) {
        return events.stream().filter(event -> event.type() == type).findFirst().orElseThrow();
    }

    private static TreeAnimationEvent last(List<TreeAnimationEvent> events) {
        return events.get(events.size() - 1);
    }

    private static long count(List<TreeAnimationEvent> events, EventType type) {
        return events.stream().filter(event -> event.type() == type).count();
    }

    private static void assertOrdered(
            List<TreeAnimationEvent> events, EventType... expected) {
        int cursor = -1;
        for (EventType type : expected) {
            boolean found = false;
            for (int index = cursor + 1; index < events.size(); index++) {
                if (events.get(index).type() == type) {
                    cursor = index;
                    found = true;
                    break;
                }
            }
            assertTrue(found, () -> type + " not found in order: "
                    + events.stream().map(TreeAnimationEvent::type).toList());
        }
    }

    private static List<Integer> range(int from, int to) {
        List<Integer> values = new ArrayList<>();
        for (int value = from; value <= to; value++) {
            values.add(value);
        }
        return values;
    }

    private static List<Integer> rangeExcept(int from, int to, int excluded) {
        List<Integer> values = range(from, to);
        values.remove(Integer.valueOf(excluded));
        return values;
    }
}
