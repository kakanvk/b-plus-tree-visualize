package com.bplustree.visualizer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bplustree.visualizer.event.OperationResult;
import com.bplustree.visualizer.event.TreeAnimationEvent;
import com.bplustree.visualizer.model.TreeSnapshot;
import com.bplustree.visualizer.service.BPlusTreeService;
import com.bplustree.visualizer.visualization.AnimationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies snapshot seeking and observable timeline state without a JavaFX toolkit. */
class AnimationStateTest {

    @Test
    void loadUpdatesEventCountWhenCurrentIndexRemainsMinusOne() {
        OperationResult operation = operationWithSnapshots();
        AnimationManager manager = new AnimationManager(event -> {});
        AtomicInteger changes = new AtomicInteger();
        manager.eventCountProperty().addListener(
            (observable, oldValue, newValue) -> changes.incrementAndGet()
        );

        manager.load(operation.events());

        assertEquals(-1, manager.getCurrentIndex());
        assertEquals(operation.events().size(), manager.getEventCount());
        assertFalse(manager.isAtEnd());

        manager.load(List.of());

        assertEquals(-1, manager.getCurrentIndex());
        assertEquals(0, manager.getEventCount());
        assertTrue(manager.isAtEnd());
        assertEquals(2, changes.get());
    }

    @Test
    void jumpPreviousNextAndRestartEmitExactEventSnapshots() {
        OperationResult operation = operationWithSnapshots();
        List<TreeAnimationEvent> shown = new ArrayList<>();
        AnimationManager manager = new AnimationManager(operation.events(), shown::add);
        int lastIndex = operation.events().size() - 1;

        manager.jumpTo(lastIndex);
        assertTrue(manager.isAtEnd());
        assertSame(operation.events().get(lastIndex), shown.getLast());

        manager.previous();
        assertEquals(lastIndex - 1, manager.getCurrentIndex());
        assertFalse(manager.isAtEnd());
        assertSame(operation.events().get(lastIndex - 1), shown.getLast());

        manager.next();
        assertEquals(lastIndex, manager.getCurrentIndex());
        assertTrue(manager.isAtEnd());
        assertSame(operation.events().get(lastIndex), shown.getLast());

        manager.restart();
        assertEquals(0, manager.getCurrentIndex());
        assertEquals(operation.events().getFirst().snapshot(), shown.getLast().snapshot());
        assertFalse(manager.isAtEnd());
    }

    @Test
    void boundaryNavigationAndSingleFrameReplayDoNotEmitDuplicates() {
        TreeAnimationEvent onlyEvent = operationWithSnapshots().events().getFirst();
        List<TreeAnimationEvent> shown = new ArrayList<>();
        AnimationManager manager = new AnimationManager(List.of(onlyEvent), shown::add);

        manager.jumpTo(0);
        manager.previous();
        manager.next();
        assertEquals(1, shown.size());

        manager.replay();
        assertEquals(2, shown.size());
        assertSame(onlyEvent, shown.getLast());
        assertTrue(manager.isAtEnd());
        assertFalse(manager.isPlaying());
    }

    @Test
    void snapshotSeekingDoesNotMutateCanonicalServiceTree() {
        BPlusTreeService service = populatedService();
        OperationResult operation = service.delete(20);
        TreeSnapshot canonical = service.getTree().snapshot();
        AnimationManager manager = new AnimationManager(operation.events(), event -> {});

        manager.jumpTo(operation.events().size() - 1);
        manager.previous();
        manager.jumpTo(0);
        manager.next();
        manager.restart();

        assertEquals(canonical, service.getTree().snapshot());
    }

    private static OperationResult operationWithSnapshots() {
        BPlusTreeService service = populatedService();
        return service.insert(15);
    }

    private static BPlusTreeService populatedService() {
        BPlusTreeService service = new BPlusTreeService(4);
        service.insert(10);
        service.insert(20);
        service.insert(30);
        return service;
    }
}
