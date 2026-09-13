package com.bplustree.visualizer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bplustree.visualizer.event.EventType;
import com.bplustree.visualizer.event.OperationResult;
import com.bplustree.visualizer.event.TreeAnimationEvent;
import com.bplustree.visualizer.model.BPlusTree;
import com.bplustree.visualizer.model.NodeSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BPlusTreeServiceTest {

    // ── Rebalance ──────────────────────────────────────────────

    @Test
    void rebalanceMutatesCanonicalTreeAndPreservesReferenceAndKeys() {
        BPlusTreeService service = new BPlusTreeService(5);
        List<Integer> input = List.of(90, 5, 70, -20, 45, 1000, 12, 18, 33, 52);
        service.getTree().insertAll(input);
        BPlusTree originalTree = service.getTree();
        List<Integer> keysBefore = originalTree.keys();

        OperationResult result = service.rebalance();

        assertTrue(result.success());
        assertSame(originalTree, service.getTree());
        assertEquals(keysBefore, service.getTree().keys());
        assertEquals(5, service.getTree().getOrder());
        assertEquals(keysBefore.size(), result.values().size());
        assertTrue(result.values().containsAll(keysBefore));
        assertEquals(service.getTree().snapshot(),
                result.events().get(result.events().size() - 1).snapshot());
        assertEquals(EventType.COMPLETE,
                result.events().get(result.events().size() - 1).type());
    }

    @Test
    void rebalanceEventsReplayExactSimulationFrames() {
        BPlusTreeService service = new BPlusTreeService(4);
        service.getTree().insertAll(List.of(10, 20, 30, 40, 50));

        OperationResult result = service.rebalance();

        assertEquals(EventType.VISIT_NODE, result.events().get(0).type());
        assertFalse(result.events().get(0).snapshot().isEmpty());
        assertEquals(EventType.VISIT_NODE, result.events().get(1).type());
        assertTrue(result.events().get(1).snapshot().isEmpty());
        assertTrue(result.events().get(1).title().contains("cây rỗng"));
        assertEquals(EventType.COMPLETE,
                result.events().get(result.events().size() - 1).type());
        assertEquals(result.values().size(), result.events().stream()
                .filter(event -> event.type() == EventType.INSERT_KEY).count());

        List<Integer> inserted = new ArrayList<>();
        for (TreeAnimationEvent event : result.events()) {
            assertTrue(event.snapshot().order() == 4);
            if (event.type() == EventType.INSERT_KEY) {
                inserted.add(event.focusKey());
                assertEquals(inserted.stream().sorted().toList(), leafKeys(event));
            }
        }
        assertEquals(service.getTree().snapshot(),
                result.events().get(result.events().size() - 1).snapshot());
        int previousComparisons = 0;
        for (TreeAnimationEvent event : result.events()) {
            assertTrue(event.comparisons() >= previousComparisons);
            previousComparisons = event.comparisons();
        }
        assertEquals(result.comparisons(), previousComparisons);
    }

    @Test
    void rejectsWorseMedianCandidateWithoutPublishingItsFrames() {
        BPlusTreeService service = new BPlusTreeService(3);
        List<Integer> insertionOrder = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        java.util.Collections.shuffle(insertionOrder, new java.util.Random(0));
        service.getTree().insertAll(insertionOrder);
        BPlusTree originalTree = service.getTree();
        var snapshotBefore = originalTree.snapshot();
        int heightBefore = originalTree.height();
        int nodeCountBefore = originalTree.statistics().nodeCount();

        OperationResult result = service.rebalance();

        assertTrue(result.success());
        assertSame(originalTree, service.getTree());
        assertEquals(snapshotBefore, service.getTree().snapshot());
        assertEquals(List.of(), result.values());
        assertEquals(1, result.events().size());
        assertEquals(snapshotBefore, result.events().get(0).snapshot());
        assertTrue(result.message().contains("đã tốt hơn"));
        assertEquals(heightBefore, service.getTree().height());
        assertEquals(nodeCountBefore, service.getTree().statistics().nodeCount());
    }

    @Test
    void acceptedRebalanceNeverWorsensHeightOrNodeCount() {
        BPlusTreeService service = new BPlusTreeService(5);
        service.getTree().insertAll(List.of(90, 5, 70, -20, 45, 1000, 12, 18, 33, 52));
        int heightBefore = service.getTree().height();
        int nodeCountBefore = service.getTree().statistics().nodeCount();

        OperationResult result = service.rebalance();

        assertFalse(result.values().isEmpty());
        assertTrue(service.getTree().height() <= heightBefore);
        assertTrue(service.getTree().statistics().nodeCount() <= nodeCountBefore);
    }

    @Test
    void rebalanceInsertionOrderPreservesAllKeys() {
        BPlusTreeService service = new BPlusTreeService(4);
        List<Integer> input = List.of(50, 10, 30, 20, 40);
        service.getTree().insertAll(input);

        OperationResult result = service.rebalance();

        // After applying insertion order, tree should have all keys
        BPlusTreeService service2 = new BPlusTreeService(4);
        result.values().forEach(k -> service2.getTree().insert(k));
        assertEquals(input.stream().sorted().toList(), service2.getTree().keys());
        assertTrue(service2.getTree().validateInvariants());
    }

    @Test
    void rejectsRebalanceForEmptyTree() {
        BPlusTreeService service = new BPlusTreeService(4);

        OperationResult result = service.rebalance();

        assertFalse(result.success());
        assertEquals(EventType.ERROR, result.events().get(0).type());
        assertTrue(service.getTree().isEmpty());
    }

    private static List<Integer> leafKeys(TreeAnimationEvent event) {
        return event.snapshot().nodes().stream()
                .filter(NodeSnapshot::leaf)
                .flatMap(node -> node.keys().stream())
                .sorted()
                .toList();
    }

    // ── Insert ─────────────────────────────────────────────────

    @Test
    void insertAddsKeyAndReturnsCorrectEvents() {
        BPlusTreeService service = new BPlusTreeService(4);
        service.getTree().insertAll(List.of(10, 20, 30));

        OperationResult result = service.insert(15);

        assertTrue(result.success());
        assertTrue(service.getTree().contains(15));
        assertEquals(4, service.getTree().keys().size());
        assertEquals(EventType.INSERT_KEY,
                result.events().stream()
                        .filter(e -> e.type() == EventType.INSERT_KEY)
                        .findFirst().orElse(null).type());
        assertEquals(EventType.COMPLETE,
                result.events().get(result.events().size() - 1).type());
    }

    @Test
    void insertDuplicateKeyFails() {
        BPlusTreeService service = new BPlusTreeService(4);
        service.getTree().insertAll(List.of(10, 20, 30));

        OperationResult result = service.insert(20);

        assertFalse(result.success());
        assertEquals(3, service.getTree().keys().size());
        assertEquals(EventType.COMPLETE,
                result.events().get(result.events().size() - 1).type());
    }

    @Test
    void insertIntoEmptyTree() {
        BPlusTreeService service = new BPlusTreeService(4);

        OperationResult result = service.insert(42);

        assertTrue(result.success());
        assertTrue(service.getTree().contains(42));
        assertEquals(1, service.getTree().keys().size());
    }

    // ── Delete ─────────────────────────────────────────────────

    @Test
    void deleteRemovesKeyAndReturnsCorrectEvents() {
        BPlusTreeService service = new BPlusTreeService(4);
        service.getTree().insertAll(List.of(10, 20, 30));

        OperationResult result = service.delete(20);

        assertTrue(result.success());
        assertFalse(service.getTree().contains(20));
        assertEquals(2, service.getTree().keys().size());
        assertEquals(EventType.DELETE_KEY,
                result.events().stream()
                        .filter(e -> e.type() == EventType.DELETE_KEY)
                        .findFirst().orElse(null).type());
        assertEquals(EventType.COMPLETE,
                result.events().get(result.events().size() - 1).type());
    }

    @Test
    void deleteNonexistentKeyFails() {
        BPlusTreeService service = new BPlusTreeService(4);
        service.getTree().insertAll(List.of(10, 20, 30));

        OperationResult result = service.delete(99);

        assertFalse(result.success());
        assertEquals(3, service.getTree().keys().size());
    }

    // ── Full flow: rebalance then insert ───────────────────────

    @Test
    void insertAfterRebalanceUsesBalancedTree() {
        BPlusTreeService service = new BPlusTreeService(4);
        List<Integer> input = List.of(50, 10, 30, 20, 40);
        service.getTree().insertAll(input);

        // Rebalance (does NOT modify tree)
        OperationResult rebalanceResult = service.rebalance();
        assertTrue(rebalanceResult.success());

        // Simulate controller: clear tree, apply insertion order
        List<Integer> keysBefore = service.getTree().keys();
        service.clear();
        rebalanceResult.values().forEach(k -> service.getTree().insert(k));

        // Now insert a new key
        OperationResult insertResult = service.insert(25);
        assertTrue(insertResult.success());
        assertTrue(service.getTree().contains(25));

        // Tree should have all original keys + new key
        List<Integer> expected = new ArrayList<>(keysBefore);
        expected.add(25);
        assertEquals(expected.stream().sorted().toList(), service.getTree().keys());
        assertTrue(service.getTree().validateInvariants());
    }

    @Test
    void deleteAfterRebalanceUsesBalancedTree() {
        BPlusTreeService service = new BPlusTreeService(4);
        List<Integer> input = List.of(50, 10, 30, 20, 40);
        service.getTree().insertAll(input);

        // Rebalance
        OperationResult rebalanceResult = service.rebalance();
        service.clear();
        rebalanceResult.values().forEach(k -> service.getTree().insert(k));

        // Delete a key
        OperationResult deleteResult = service.delete(30);
        assertTrue(deleteResult.success());
        assertFalse(service.getTree().contains(30));
        assertEquals(4, service.getTree().keys().size());
        assertTrue(service.getTree().validateInvariants());
    }

    // ── Sequence of operations ─────────────────────────────────

    @Test
    void multipleInsertsMaintainInvariants() {
        BPlusTreeService service = new BPlusTreeService(4);
        for (int i = 1; i <= 20; i++) {
            OperationResult result = service.insert(i);
            assertTrue(result.success(), "Failed to insert " + i);
            assertTrue(service.getTree().validateInvariants(),
                    "Invariants broken after inserting " + i);
        }
        assertEquals(20, service.getTree().keys().size());
    }

    @Test
    void insertDeleteInsertCycle() {
        BPlusTreeService service = new BPlusTreeService(4);
        service.getTree().insertAll(List.of(10, 20, 30, 40, 50));

        // Delete
        assertTrue(service.delete(30).success());
        assertFalse(service.getTree().contains(30));

        // Insert
        assertTrue(service.insert(35).success());
        assertTrue(service.getTree().contains(35));

        // Delete
        assertTrue(service.delete(10).success());
        assertFalse(service.getTree().contains(10));

        // Insert
        assertTrue(service.insert(5).success());
        assertTrue(service.getTree().contains(5));

        assertEquals(List.of(5, 20, 35, 40, 50), service.getTree().keys());
        assertTrue(service.getTree().validateInvariants());
    }

    @Test
    void searchAfterOperations() {
        BPlusTreeService service = new BPlusTreeService(4);
        service.getTree().insertAll(List.of(10, 20, 30, 40, 50));

        OperationResult searchResult = service.search(30);
        assertTrue(searchResult.success());
    }
}
