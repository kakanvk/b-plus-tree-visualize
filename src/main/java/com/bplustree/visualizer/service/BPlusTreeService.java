package com.bplustree.visualizer.service;

import com.bplustree.visualizer.event.EventTarget;
import com.bplustree.visualizer.event.EventType;
import com.bplustree.visualizer.event.OperationResult;
import com.bplustree.visualizer.event.OperationTrace;
import com.bplustree.visualizer.event.TreeAnimationEvent;
import com.bplustree.visualizer.model.BPlusTree;
import java.util.ArrayList;
import java.util.List;

/** Coordinates model operations and exposes their direct checkpoint traces. */
public final class BPlusTreeService {
    private BPlusTree tree;

    public BPlusTreeService(int order) {
        tree = new BPlusTree(order);
    }

    public synchronized BPlusTree getTree() {
        return tree;
    }

    public synchronized void reset(int order) {
        tree = new BPlusTree(order);
    }

    public synchronized OperationResult insert(int key) {
        OperationTrace<Boolean> trace = tree.insertTraced(key);
        String message = trace.result()
                ? "Đã chèn khóa " + key + "."
                : "Khóa " + key + " đã có trong cây.";
        return result(trace.result(), message, trace.events(),
                trace.result() ? List.of(key) : List.of(), trace.comparisons());
    }

    public synchronized OperationResult delete(int key) {
        OperationTrace<Boolean> trace = tree.deleteTraced(key);
        String message = trace.result()
                ? "Đã xóa khóa " + key + "."
                : "Khóa " + key + " không có trong cây.";
        return result(trace.result(), message, trace.events(),
                trace.result() ? List.of(key) : List.of(), trace.comparisons());
    }

    public synchronized OperationResult search(int key) {
        OperationTrace<Boolean> trace = tree.searchTraced(key);
        String message = trace.result()
                ? "Đã tìm thấy khóa " + key + "."
                : "Khóa " + key + " không có trong cây.";
        return result(trace.result(), message, trace.events(),
                trace.result() ? List.of(key) : List.of(), trace.comparisons());
    }

    /**
     * Rebuilds the canonical tree using median-first insertion.
     *
     * <p>A separate candidate is evaluated first. If accepted, the existing
     * canonical tree object is cleared and rebuilt with traced insertions, so
     * references returned earlier by {@link #getTree()} remain valid.</p>
     */
    public synchronized OperationResult rebalance() {
        if (tree.isEmpty()) {
            String message = "Không thể cân bằng vì cây đang trống.";
            TreeAnimationEvent error = new TreeAnimationEvent(
                    EventType.ERROR,
                    "Cây đang trống",
                    "Hãy thêm khóa trước khi cân bằng.",
                    tree.snapshot(),
                    EventTarget.none());
            return result(false, message, List.of(error), List.of(), 0);
        }

        List<Integer> keys = tree.keys();
        List<Integer> insertionOrder = new ArrayList<>(keys.size());
        appendMedianFirst(keys, 0, keys.size(), insertionOrder);

        int heightBefore = tree.height();
        int nodeCountBefore = tree.statistics().nodeCount();
        BPlusTree candidate = new BPlusTree(tree.getOrder());
        candidate.insertAll(insertionOrder);
        boolean candidateAccepted = candidate.height() <= heightBefore
                && candidate.statistics().nodeCount() <= nodeCountBefore;

        if (!candidateAccepted) {
            String message = "Giữ nguyên cây vì cấu trúc hiện tại đã tốt hơn candidate median-first "
                    + "(height " + heightBefore + " so với " + candidate.height()
                    + ", nodeCount " + nodeCountBefore + " so với "
                    + candidate.statistics().nodeCount() + ").";
            TreeAnimationEvent complete = new TreeAnimationEvent(
                    EventType.COMPLETE,
                    "Giữ nguyên cây hiện tại",
                    message,
                    tree.snapshot(),
                    EventTarget.none().withKeys(null, keys),
                    0);
            return result(true, message, List.of(complete), List.of(), 0);
        }

        List<TreeAnimationEvent> events = new ArrayList<>();
        events.add(new TreeAnimationEvent(
                EventType.VISIT_NODE,
                "Chuẩn bị dựng lại cây",
                "Candidate không làm tăng height hoặc nodeCount; lưu frame cây hiện tại trước khi dựng lại.",
                tree.snapshot(),
                EventTarget.none().withKeys(keys.get(0), keys),
                0));
        tree.clear();
        events.add(new TreeAnimationEvent(
                EventType.VISIT_NODE,
                "Chuyển sang cây rỗng",
                "Đã xóa cấu trúc cũ; bắt đầu traced insertion trên cùng object cây canonical.",
                tree.snapshot(),
                EventTarget.none(),
                0));

        int comparisons = 0;
        for (int key : insertionOrder) {
            OperationTrace<Boolean> insertion = tree.insertTraced(key);
            if (!insertion.result()) {
                throw new IllegalStateException("Median-first rebuild produced a duplicate key");
            }
            for (TreeAnimationEvent event : insertion.events()) {
                TreeAnimationEvent adjusted = withComparisonOffset(event, comparisons);
                boolean redundantBoundaryVisit = adjusted.type() == EventType.VISIT_NODE
                        && !events.isEmpty()
                        && events.get(events.size() - 1).snapshot().equals(adjusted.snapshot());
                if (!redundantBoundaryVisit) {
                    events.add(adjusted);
                }
            }
            comparisons += insertion.comparisons();
        }

        String message = "Đã cân bằng lại cây mà không làm tăng height hoặc nodeCount.";
        return result(true, message, events, insertionOrder, comparisons);
    }

    public synchronized void clear() {
        tree.clear();
    }

    private static TreeAnimationEvent withComparisonOffset(
            TreeAnimationEvent event, int offset) {
        return new TreeAnimationEvent(
                event.type(), event.title(), event.detail(), event.snapshot(), event.target(),
                offset + event.comparisons());
    }

    private static void appendMedianFirst(
            List<Integer> sortedKeys,
            int fromInclusive,
            int toExclusive,
            List<Integer> insertionOrder) {
        if (fromInclusive >= toExclusive) {
            return;
        }
        int middle = (fromInclusive + toExclusive) >>> 1;
        insertionOrder.add(sortedKeys.get(middle));
        appendMedianFirst(sortedKeys, fromInclusive, middle, insertionOrder);
        appendMedianFirst(sortedKeys, middle + 1, toExclusive, insertionOrder);
    }

    private static OperationResult result(
            boolean success,
            String message,
            List<TreeAnimationEvent> events,
            List<Integer> values,
            int comparisons) {
        return new OperationResult(success, message, events, values, comparisons);
    }
}
