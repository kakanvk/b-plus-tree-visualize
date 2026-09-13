package com.bplustree.visualizer.model;

import com.bplustree.visualizer.event.EventTarget;
import com.bplustree.visualizer.event.EventType;
import com.bplustree.visualizer.event.OperationTrace;
import com.bplustree.visualizer.event.TreeAnimationEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An in-memory B+ tree for unique integer keys.
 *
 * <p>The order is the maximum number of children in an internal node. Leaves
 * contain at most {@code order - 1} keys. Internal separator keys equal the
 * smallest key in the child immediately to their right.</p>
 */
public final class BPlusTree {
    public static final int MIN_ORDER = 3;
    public static final int MAX_ORDER = 8;

    private final int order;
    private final int maxLeafKeys;
    private final int splitIndex;
    private BPlusNode root;
    private int size;

    public BPlusTree(int order) {
        if (order < MIN_ORDER || order > MAX_ORDER) {
            throw new IllegalArgumentException(
                    "Order must be between " + MIN_ORDER + " and " + MAX_ORDER);
        }
        this.order = order;
        this.maxLeafKeys = order - 1;
        this.splitIndex = order / 2;
        this.root = new LeafNode();
    }

    public int getOrder() {
        return order;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Returns the root for read-only structural inspection. */
    public BPlusNode getRoot() {
        return root;
    }

    /** Captures the current immutable tree state. */
    public TreeSnapshot snapshot() {
        List<NodeSnapshot> nodes = new ArrayList<>();
        List<BPlusNode> pending = new ArrayList<>();
        pending.add(root);
        for (int index = 0; index < pending.size(); index++) {
            BPlusNode node = pending.get(index);
            List<Long> childIds = List.of();
            Long previousId = null;
            Long nextId = null;
            if (node instanceof InternalNode internal) {
                childIds = internal.children.stream().map(BPlusNode::getId).toList();
                pending.addAll(internal.children);
            } else {
                LeafNode leaf = (LeafNode) node;
                previousId = leaf.previous == null ? null : leaf.previous.getId();
                nextId = leaf.next == null ? null : leaf.next.getId();
            }
            nodes.add(new NodeSnapshot(
                    node.getId(), node.isLeaf(), node.getKeys(), childIds,
                    node.parent == null ? null : node.parent.getId(), previousId, nextId));
        }
        return new TreeSnapshot(order, size, root.getId(), nodes);
    }

    public int height() {
        int height = 1;
        BPlusNode current = root;
        while (!current.isLeaf()) {
            height++;
            current = ((InternalNode) current).children.get(0);
        }
        return height;
    }

    public boolean search(int key) {
        LeafNode leaf = findLeaf(key, null);
        return Collections.binarySearch(leaf.keys, key) >= 0;
    }

    /** Searches while capturing every traversal checkpoint. */
    public OperationTrace<Boolean> searchTraced(int key) {
        TraceCollector trace = new TraceCollector();
        LeafNode leaf = findLeaf(key, trace);
        boolean found = Collections.binarySearch(leaf.keys, key) >= 0;
        if (found) {
            int keyIndex = Collections.binarySearch(leaf.keys, key);
            trace.add(EventType.MATCH_KEY, "Tìm thấy khóa",
                    "Khóa " + key + " khớp tại nút lá.",
                    EventTarget.key(leaf.getId(), keyIndex, key));
        }
        trace.complete(found ? "Tìm kiếm hoàn tất" : "Không tìm thấy khóa",
                found ? "Đã tìm thấy khóa " + key + "."
                        : "Nút lá đích không chứa khóa " + key + ".",
                EventTarget.node(leaf.getId(), key, found ? List.of(key) : List.of()));
        return trace.result(found);
    }

    public boolean contains(int key) {
        return search(key);
    }

    /** Inserts a key if it is absent. */
    public boolean insert(int key) {
        return insertInternal(key, null);
    }

    /** Inserts while capturing direct mutation checkpoints. */
    public OperationTrace<Boolean> insertTraced(int key) {
        TraceCollector trace = new TraceCollector();
        boolean inserted = insertInternal(key, trace);
        return trace.result(inserted);
    }

    private boolean insertInternal(int key, TraceCollector trace) {
        LeafNode leaf = findLeaf(key, trace);
        int position = Collections.binarySearch(leaf.keys, key);
        if (position >= 0) {
            if (trace != null) {
                trace.complete("Bỏ qua thao tác chèn",
                        "Khóa " + key + " đã có trong cây; cây chỉ lưu khóa duy nhất.",
                        EventTarget.key(leaf.getId(), position, key));
            }
            return false;
        }

        int insertionIndex = -position - 1;
        leaf.keys.add(insertionIndex, key);
        size++;
        boolean overflow = leaf.keys.size() > maxLeafKeys;
        if (trace != null) {
            trace.add(EventType.INSERT_KEY, overflow ? "Chèn khóa làm nút lá bị tràn" : "Chèn khóa",
                    overflow
                            ? "Đã chèn khóa " + key + "; nút lá tạm thời có "
                                    + leaf.keys.size() + " khóa và phải được tách."
                            : "Đặt khóa " + key + " vào vị trí tăng dần trong nút lá.",
                    EventTarget.key(leaf.getId(), insertionIndex, key));
        }
        if (overflow) {
            splitLeaf(leaf, trace);
        }
        recomputeSeparators(root);
        if (trace != null) {
            trace.complete("Chèn khóa hoàn tất",
                    "Đã chèn khóa " + key + " và xử lý xong mọi split lan truyền.",
                    EventTarget.node(findLeaf(key, null).getId(), key, List.of(key)));
        }
        return true;
    }

    /** Inserts every key and returns the number of newly inserted keys. */
    public int insertAll(Collection<Integer> keys) {
        Objects.requireNonNull(keys, "keys");
        int inserted = 0;
        for (Integer key : keys) {
            if (insert(Objects.requireNonNull(key, "keys cannot contain null"))) {
                inserted++;
            }
        }
        return inserted;
    }

    /** Deletes a key if present. */
    public boolean delete(int key) {
        return deleteInternal(key, null);
    }

    /** Deletes while capturing direct mutation and repair checkpoints. */
    public OperationTrace<Boolean> deleteTraced(int key) {
        TraceCollector trace = new TraceCollector();
        boolean deleted = deleteInternal(key, trace);
        return trace.result(deleted);
    }

    private boolean deleteInternal(int key, TraceCollector trace) {
        LeafNode leaf = findLeaf(key, trace);
        int position = Collections.binarySearch(leaf.keys, key);
        if (position < 0) {
            if (trace != null) {
                trace.complete("Bỏ qua thao tác xóa",
                        "Khóa " + key + " không có trong nút lá đích.",
                        EventTarget.node(leaf.getId(), key, leaf.keys));
            }
            return false;
        }

        if (trace != null) {
            trace.add(EventType.MATCH_KEY, "Xác định khóa cần xóa",
                    "Khóa " + key + " khớp tại vị trí " + (position + 1) + " của nút lá.",
                    EventTarget.key(leaf.getId(), position, key));
        }
        leaf.keys.remove(position);
        size--;
        recomputeSeparators(root);
        boolean underflow = leaf != root && leaf.keys.size() < minLeafKeys();
        if (trace != null) {
            trace.add(EventType.DELETE_KEY, underflow ? "Xóa khóa làm nút lá thiếu khóa" : "Xóa khóa",
                    "Đã xóa khóa " + key + " khỏi nút lá và cập nhật mọi khóa phân cách ancestor"
                            + (underflow ? "; nút lá cần được repair." : "."),
                    targetWithAncestors(leaf, List.of(), key, leaf.keys));
        }

        if (underflow) {
            rebalanceLeaf(leaf, trace);
        }
        shrinkRootIfNeeded(trace);
        recomputeSeparators(root);
        if (trace != null) {
            trace.complete("Xóa khóa hoàn tất",
                    "Đã xóa khóa " + key
                            + ", cập nhật mọi separator ancestor và xử lý xong mọi repair lan truyền.",
                    EventTarget.none().withKeys(key, List.of()));
        }
        return true;
    }

    /** Returns all keys in ascending order. */
    public List<Integer> keys() {
        if (size == 0) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>(size);
        LeafNode leaf = firstLeaf();
        while (leaf != null) {
            result.addAll(leaf.keys);
            leaf = leaf.next;
        }
        return List.copyOf(result);
    }

    /** Returns keys in the inclusive interval. */
    public List<Integer> rangeSearch(int fromInclusive, int toInclusive) {
        return rangeSearchInternal(fromInclusive, toInclusive, null);
    }

    /** Range-searches while capturing tree descent and leaf-link traversal. */
    public OperationTrace<List<Integer>> rangeSearchTraced(int fromInclusive, int toInclusive) {
        TraceCollector trace = new TraceCollector();
        if (fromInclusive > toInclusive) {
            trace.add(EventType.ERROR, "Khoảng tìm kiếm không hợp lệ",
                    "Giá trị đầu khoảng không được lớn hơn giá trị cuối khoảng.",
                    EventTarget.none().withKeys(fromInclusive, List.of(fromInclusive, toInclusive)));
            return trace.result(List.of());
        }
        List<Integer> values = rangeSearchInternal(fromInclusive, toInclusive, trace);
        trace.complete("Tìm kiếm theo khoảng hoàn tất",
                values.isEmpty() ? "Không có khóa thuộc khoảng yêu cầu."
                        : "Đã tìm thấy " + values.size() + " khóa trong khoảng.",
                EventTarget.none().withKeys(null, values));
        return trace.result(values);
    }

    private List<Integer> rangeSearchInternal(
            int fromInclusive, int toInclusive, TraceCollector trace) {
        if (fromInclusive > toInclusive || size == 0) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        LeafNode leaf = findLeaf(fromInclusive, trace);
        while (leaf != null) {
            if (trace != null) {
                trace.add(EventType.HIGHLIGHT_RANGE, "Kiểm tra nút lá trong khoảng",
                        "Kiểm tra các khóa thuộc [" + fromInclusive + ", " + toInclusive + "].",
                        EventTarget.node(leaf.getId(), null, leaf.keys));
            }
            for (int index = 0; index < leaf.keys.size(); index++) {
                int key = leaf.keys.get(index);
                if (trace != null) {
                    trace.comparison();
                    trace.add(EventType.COMPARE_KEY, "So sánh khóa với khoảng",
                            key + " được đối chiếu với [" + fromInclusive + ", "
                                    + toInclusive + "].",
                            EventTarget.key(leaf.getId(), index, key));
                }
                if (key < fromInclusive) {
                    continue;
                }
                if (key > toInclusive) {
                    return List.copyOf(result);
                }
                result.add(key);
            }
            LeafNode next = leaf.next;
            boolean nextMayOverlap = next != null;
            if (nextMayOverlap && !next.keys.isEmpty()) {
                int nextMinimum = next.keys.get(0);
                if (trace != null) {
                    trace.comparison();
                    trace.add(EventType.COMPARE_KEY, "Kiểm tra giới hạn nút lá kế tiếp",
                            "Khóa nhỏ nhất " + nextMinimum + " của nút lá kế tiếp "
                                    + (nextMinimum <= toInclusive ? "còn" : "không còn")
                                    + " thuộc giới hạn trên " + toInclusive + ".",
                            EventTarget.key(next.getId(), 0, nextMinimum));
                }
                nextMayOverlap = nextMinimum <= toInclusive;
            }
            if (trace != null && nextMayOverlap) {
                trace.add(EventType.TRAVERSE_EDGE, "Đi theo liên kết nút lá",
                        "Tiếp tục tìm kiếm ở nút lá kế tiếp.",
                        new EventTarget(leaf.getId(), List.of(next.getId()), null,
                                null, null, List.of()));
            }
            leaf = nextMayOverlap ? next : null;
        }
        return List.copyOf(result);
    }

    public void clear() {
        root = new LeafNode();
        size = 0;
    }

    private LeafNode findLeaf(int key, TraceCollector trace) {
        BPlusNode current = root;
        while (true) {
            if (trace != null) {
                trace.add(EventType.VISIT_NODE,
                        current.isLeaf() ? "Duyệt nút lá" : "Duyệt nút trong",
                        current.isLeaf()
                                ? "Đây là nút lá mà khóa " + key + " cần thuộc về."
                                : "Kiểm tra khóa phân cách để chọn nút con.",
                        EventTarget.node(current.getId(), null, current.getKeys()));
            }
            if (current.isLeaf()) {
                if (trace != null) {
                    for (int index = 0; index < current.getKeys().size(); index++) {
                        int candidate = current.getKeys().get(index);
                        trace.comparison();
                        trace.add(EventType.COMPARE_KEY, "So sánh khóa trong nút lá",
                                comparisonDetail(key, candidate),
                                EventTarget.key(current.getId(), index, candidate)
                                        .withKeys(candidate, List.of(key)));
                        if (key <= candidate) {
                            break;
                        }
                    }
                }
                return (LeafNode) current;
            }

            InternalNode internal = (InternalNode) current;
            int childIndex = 0;
            for (int index = 0; index < internal.keys.size(); index++) {
                int separator = internal.keys.get(index);
                if (trace != null) {
                    trace.comparison();
                    trace.add(EventType.COMPARE_KEY, "So sánh khóa phân cách",
                            comparisonDetail(key, separator),
                            EventTarget.key(internal.getId(), index, separator)
                                    .withKeys(separator, List.of(key)));
                }
                if (key < separator) {
                    break;
                }
                childIndex++;
            }
            BPlusNode child = internal.children.get(childIndex);
            if (trace != null) {
                trace.add(EventType.TRAVERSE_EDGE, "Đi theo con trỏ tới nút con",
                        "Đi từ nút " + internal.getId() + " tới nút con thứ "
                                + (childIndex + 1) + ".",
                        EventTarget.edge(internal.getId(), child.getId(), childIndex));
            }
            current = child;
        }
    }

    private static String comparisonDetail(int target, int candidate) {
        if (target == candidate) {
            return target + " bằng " + candidate + ".";
        }
        return target < candidate
                ? target + " nhỏ hơn " + candidate + "."
                : target + " lớn hơn " + candidate + ".";
    }

    private static int upperBound(List<Integer> keys, int value) {
        int low = 0;
        int high = keys.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (value < keys.get(middle)) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private void splitLeaf(LeafNode leaf, TraceCollector trace) {
        LeafNode right = new LeafNode();
        right.keys.addAll(leaf.keys.subList(splitIndex, leaf.keys.size()));
        leaf.keys.subList(splitIndex, leaf.keys.size()).clear();
        right.next = leaf.next;
        if (right.next != null) {
            right.next.previous = right;
        }
        leaf.next = right;
        right.previous = leaf;
        attachRightSibling(leaf, right, right.keys.get(0), trace);
    }

    private void splitInternal(InternalNode node, TraceCollector trace) {
        int rightSplit = splitIndex + 1;
        InternalNode right = new InternalNode();
        right.children.addAll(node.children.subList(rightSplit, node.children.size()));
        node.children.subList(rightSplit, node.children.size()).clear();
        for (BPlusNode child : right.children) {
            child.parent = right;
        }
        recomputeSeparators(node);
        recomputeSeparators(right);
        Integer promoted = subtreeMinimum(right);
        attachRightSibling(node, right, promoted, trace);
    }

    private void attachRightSibling(
            BPlusNode left, BPlusNode right, Integer promoted, TraceCollector trace) {
        InternalNode parent = left.parent;
        boolean createsRoot = parent == null;
        if (createsRoot) {
            parent = new InternalNode();
            parent.children.add(left);
            parent.children.add(right);
            left.parent = parent;
            right.parent = parent;
            root = parent;
        } else {
            int leftIndex = parent.children.indexOf(left);
            if (leftIndex < 0) {
                throw new IllegalStateException("Parent does not contain split child");
            }
            parent.children.add(leftIndex + 1, right);
            right.parent = parent;
        }
        recomputeSeparators(root);

        if (trace != null) {
            String kind = left.isLeaf() ? "nút lá" : "nút trong";
            EventType structuralType = createsRoot ? EventType.CREATE_ROOT : EventType.SPLIT_NODE;
            trace.add(structuralType,
                    createsRoot ? "Tách nút và tạo gốc mới" : "Tách " + kind + " vào nút cha",
                    "Chia " + kind + " bị tràn thành hai nút và đưa separator "
                            + promoted + (createsRoot ? " vào gốc mới."
                                    : " vào nút cha; mọi separator ancestor đã được cập nhật."),
                    EventTarget.nodes(parent.getId(), List.of(left.getId(), right.getId()))
                            .withKeys(promoted,
                                    promoted == null ? List.of() : List.of(promoted)));
        }

        if (parent.children.size() > order) {
            splitInternal(parent, trace);
        }
    }

    private void rebalanceLeaf(LeafNode leaf, TraceCollector trace) {
        InternalNode parent = leaf.parent;
        int index = parent.children.indexOf(leaf);
        LeafNode left = index > 0 ? (LeafNode) parent.children.get(index - 1) : null;
        LeafNode right = index + 1 < parent.children.size()
                ? (LeafNode) parent.children.get(index + 1) : null;

        if (left != null && left.keys.size() > minLeafKeys()) {
            int borrowed = left.keys.remove(left.keys.size() - 1);
            leaf.keys.add(0, borrowed);
            recomputeSeparators(root);
            if (trace != null) {
                trace.add(EventType.BORROW_KEY, "Mượn khóa từ nút lá bên trái",
                        "Chuyển khóa " + borrowed
                                + " từ nút anh em và cập nhật mọi separator ancestor.",
                        targetWithAncestors(leaf, List.of(left.getId()), borrowed, List.of(borrowed)));
            }
            return;
        }
        if (right != null && right.keys.size() > minLeafKeys()) {
            int borrowed = right.keys.remove(0);
            leaf.keys.add(borrowed);
            recomputeSeparators(root);
            if (trace != null) {
                trace.add(EventType.BORROW_KEY, "Mượn khóa từ nút lá bên phải",
                        "Chuyển khóa " + borrowed
                                + " từ nút anh em và cập nhật mọi separator ancestor.",
                        targetWithAncestors(leaf, List.of(right.getId()), borrowed, List.of(borrowed)));
            }
            return;
        }

        LeafNode survivor = left != null ? left : leaf;
        LeafNode removed = left != null ? leaf : right;
        if (trace != null) {
            trace.add(EventType.MERGE_NODE, "Chuẩn bị gộp các nút lá",
                    "Đánh dấu hai nút lá và nút cha trước khi thực hiện merge.",
                    EventTarget.nodes(survivor.getId(), List.of(removed.getId(), parent.getId())));
        }
        if (left != null) {
            left.keys.addAll(leaf.keys);
            left.next = leaf.next;
            if (leaf.next != null) {
                leaf.next.previous = left;
            }
            parent.children.remove(index);
        } else {
            leaf.keys.addAll(right.keys);
            leaf.next = right.next;
            if (right.next != null) {
                right.next.previous = leaf;
            }
            parent.children.remove(index + 1);
        }
        recomputeSeparators(root);
        repairInternalAfterChildRemoval(parent, trace);
    }

    private void repairInternalAfterChildRemoval(InternalNode node, TraceCollector trace) {
        recomputeSeparators(root);
        if (node == root) {
            shrinkRootIfNeeded(trace);
            return;
        }
        if (node.children.size() >= minInternalChildren()) {
            return;
        }
        if (trace != null) {
            trace.add(EventType.NODE_UNDERFLOW, "Nút trong thiếu nút con",
                    "Merge trước đã cập nhật mọi separator ancestor; nút trong còn "
                            + node.children.size() + " nút con, thấp hơn mức tối thiểu "
                            + minInternalChildren() + ".",
                    targetWithAncestors(node, List.of(), null, List.of()));
        }

        InternalNode parent = node.parent;
        int index = parent.children.indexOf(node);
        InternalNode left = index > 0 ? (InternalNode) parent.children.get(index - 1) : null;
        InternalNode right = index + 1 < parent.children.size()
                ? (InternalNode) parent.children.get(index + 1) : null;

        if (left != null && left.children.size() > minInternalChildren()) {
            BPlusNode borrowed = left.children.remove(left.children.size() - 1);
            node.children.add(0, borrowed);
            borrowed.parent = node;
            recomputeSeparators(root);
            if (trace != null) {
                trace.add(EventType.BORROW_KEY, "Mượn nhánh từ nút trong bên trái",
                        "Chuyển một nhánh con và cập nhật mọi separator ancestor.",
                        targetWithAncestors(node, List.of(left.getId(), borrowed.getId()),
                                null, List.of()));
            }
            return;
        }
        if (right != null && right.children.size() > minInternalChildren()) {
            BPlusNode borrowed = right.children.remove(0);
            node.children.add(borrowed);
            borrowed.parent = node;
            recomputeSeparators(root);
            if (trace != null) {
                trace.add(EventType.BORROW_KEY, "Mượn nhánh từ nút trong bên phải",
                        "Chuyển một nhánh con và cập nhật mọi separator ancestor.",
                        targetWithAncestors(node, List.of(right.getId(), borrowed.getId()),
                                null, List.of()));
            }
            return;
        }

        InternalNode survivor = left != null ? left : node;
        InternalNode removed = left != null ? node : right;
        if (trace != null) {
            trace.add(EventType.MERGE_NODE, "Chuẩn bị gộp các nút trong",
                    "Đánh dấu hai nút trong và nút cha trước khi thực hiện merge.",
                    EventTarget.nodes(survivor.getId(), List.of(removed.getId(), parent.getId())));
        }
        if (left != null) {
            for (BPlusNode child : node.children) {
                left.children.add(child);
                child.parent = left;
            }
            parent.children.remove(index);
        } else {
            for (BPlusNode child : right.children) {
                node.children.add(child);
                child.parent = node;
            }
            parent.children.remove(index + 1);
        }
        recomputeSeparators(root);
        repairInternalAfterChildRemoval(parent, trace);
    }

    private EventTarget targetWithAncestors(
            BPlusNode node, List<Long> relatedNodeIds, Integer focusKey, List<Integer> relatedKeys) {
        List<Long> targets = new ArrayList<>(relatedNodeIds);
        InternalNode ancestor = node.parent;
        while (ancestor != null) {
            if (!targets.contains(ancestor.getId())) {
                targets.add(ancestor.getId());
            }
            ancestor = ancestor.parent;
        }
        return new EventTarget(node.getId(), targets, null, null, focusKey, relatedKeys);
    }

    private void shrinkRootIfNeeded(TraceCollector trace) {
        while (root instanceof InternalNode internal && internal.children.size() == 1) {
            BPlusNode newRoot = internal.children.get(0);
            if (trace != null) {
                trace.add(EventType.SHRINK_ROOT, "Chuẩn bị thu nhỏ nút gốc",
                        "Merge đã hoàn tất và mọi separator ancestor đã được cập nhật; "
                                + "đánh dấu gốc cũ một nhánh trước khi thay thế.",
                        EventTarget.nodes(internal.getId(), List.of(newRoot.getId())));
            }
            root = newRoot;
            root.parent = null;
        }
    }

    private int minLeafKeys() {
        return splitIndex;
    }

    private int minInternalChildren() {
        return (order + 1) / 2;
    }

    private LeafNode firstLeaf() {
        BPlusNode current = root;
        while (!current.isLeaf()) {
            current = ((InternalNode) current).children.get(0);
        }
        return (LeafNode) current;
    }

    private Integer recomputeSeparators(BPlusNode node) {
        if (node.isLeaf()) {
            LeafNode leaf = (LeafNode) node;
            return leaf.keys.isEmpty() ? null : leaf.keys.get(0);
        }
        InternalNode internal = (InternalNode) node;
        internal.keys.clear();
        Integer minimum = null;
        for (int index = 0; index < internal.children.size(); index++) {
            Integer childMinimum = recomputeSeparators(internal.children.get(index));
            if (index == 0) {
                minimum = childMinimum;
            } else if (childMinimum != null) {
                internal.keys.add(childMinimum);
            }
        }
        return minimum;
    }

    /** Performs a complete invariant check and returns all discovered errors. */
    public ValidationResult validationResult() {
        List<String> errors = new ArrayList<>();
        List<LeafNode> structuralLeaves = new ArrayList<>();
        int[] leafDepth = {-1};
        validateNode(root, null, 0, true, leafDepth, structuralLeaves, errors);

        int countedKeys = 0;
        Integer previousKey = null;
        LeafNode previousLeaf = null;
        LeafNode linked = structuralLeaves.isEmpty() ? null : structuralLeaves.get(0);
        int linkedIndex = 0;
        while (linked != null) {
            if (linkedIndex >= structuralLeaves.size() || linked != structuralLeaves.get(linkedIndex)) {
                errors.add("Leaf next-links do not match structural leaf order");
                break;
            }
            if (linked.previous != previousLeaf) {
                errors.add("Broken previous leaf link at leaf index " + linkedIndex);
            }
            for (int key : linked.keys) {
                if (previousKey != null && key <= previousKey) {
                    errors.add("Leaf chain keys are not globally strict ascending");
                }
                previousKey = key;
                countedKeys++;
            }
            previousLeaf = linked;
            linked = linked.next;
            linkedIndex++;
            if (linkedIndex > structuralLeaves.size()) {
                errors.add("Cycle detected in leaf links");
                break;
            }
        }
        if (linkedIndex != structuralLeaves.size()) {
            errors.add("Leaf chain does not include every leaf");
        }
        if (!structuralLeaves.isEmpty() && structuralLeaves.get(0).previous != null) {
            errors.add("First leaf has a previous link");
        }
        if (!structuralLeaves.isEmpty()
                && structuralLeaves.get(structuralLeaves.size() - 1).next != null) {
            errors.add("Last leaf has a next link");
        }
        if (countedKeys != size) {
            errors.add("Stored size " + size + " differs from counted keys " + countedKeys);
        }
        return new ValidationResult(errors.isEmpty(), errors);
    }

    public boolean validateInvariants() {
        return validationResult().valid();
    }

    public void assertValid() {
        ValidationResult result = validationResult();
        if (!result.valid()) {
            throw new IllegalStateException(String.join("; ", result.errors()));
        }
    }

    public BPlusTreeStatistics statistics() {
        int internalNodes = 0;
        int leafNodes = 0;
        int nodeCount = 0;
        List<BPlusNode> pending = new ArrayList<>();
        pending.add(root);
        for (int index = 0; index < pending.size(); index++) {
            BPlusNode node = pending.get(index);
            nodeCount++;
            if (node.isLeaf()) {
                leafNodes++;
            } else {
                internalNodes++;
                pending.addAll(((InternalNode) node).children);
            }
        }
        Integer minimum = size == 0 ? null : firstLeaf().keys.get(0);
        LeafNode last = firstLeaf();
        while (last.next != null) {
            last = last.next;
        }
        Integer maximum = size == 0 ? null : last.keys.get(last.keys.size() - 1);
        double fill = leafNodes == 0 ? 0.0 : (double) size / (leafNodes * maxLeafKeys);
        return new BPlusTreeStatistics(
                order, height(), size, nodeCount, internalNodes, leafNodes, minimum, maximum, fill);
    }

    private void validateNode(
            BPlusNode node,
            InternalNode expectedParent,
            int depth,
            boolean isRoot,
            int[] leafDepth,
            List<LeafNode> leaves,
            List<String> errors) {
        if (node.parent != expectedParent) {
            errors.add("Incorrect parent pointer at depth " + depth);
        }
        if (!isStrictAscending(node.getKeys())) {
            errors.add("Node keys are not strict ascending at depth " + depth);
        }
        if (node.isLeaf()) {
            LeafNode leaf = (LeafNode) node;
            if (leaf.keys.size() > maxLeafKeys) {
                errors.add("Leaf exceeds maximum key count");
            }
            if (!isRoot && leaf.keys.size() < minLeafKeys()) {
                errors.add("Non-root leaf is below minimum occupancy");
            }
            if (leafDepth[0] < 0) {
                leafDepth[0] = depth;
            } else if (leafDepth[0] != depth) {
                errors.add("Leaves occur at different depths");
            }
            leaves.add(leaf);
            return;
        }

        InternalNode internal = (InternalNode) node;
        if (internal.children.isEmpty()) {
            errors.add("Internal node has no children");
            return;
        }
        if (internal.children.size() > order) {
            errors.add("Internal node exceeds maximum child count");
        }
        if (isRoot && internal.children.size() < 2) {
            errors.add("Internal root must have at least two children");
        }
        if (!isRoot && internal.children.size() < minInternalChildren()) {
            errors.add("Non-root internal node is below minimum occupancy");
        }
        if (internal.keys.size() != internal.children.size() - 1) {
            errors.add("Internal key count does not equal child count minus one");
        }
        for (int index = 0; index < internal.children.size(); index++) {
            BPlusNode child = internal.children.get(index);
            validateNode(child, internal, depth + 1, false, leafDepth, leaves, errors);
            if (index > 0 && index - 1 < internal.keys.size()) {
                Integer expectedSeparator = subtreeMinimum(child);
                if (expectedSeparator == null
                        || !internal.keys.get(index - 1).equals(expectedSeparator)) {
                    errors.add("Incorrect separator at depth " + depth + ", index " + (index - 1));
                }
            }
        }
    }

    private static Integer subtreeMinimum(BPlusNode node) {
        BPlusNode current = node;
        while (!current.isLeaf()) {
            InternalNode internal = (InternalNode) current;
            if (internal.children.isEmpty()) {
                return null;
            }
            current = internal.children.get(0);
        }
        LeafNode leaf = (LeafNode) current;
        return leaf.keys.isEmpty() ? null : leaf.keys.get(0);
    }

    private static boolean isStrictAscending(List<Integer> keys) {
        for (int index = 1; index < keys.size(); index++) {
            if (keys.get(index - 1) >= keys.get(index)) {
                return false;
            }
        }
        return true;
    }

    private final class TraceCollector {
        private final List<TreeAnimationEvent> events = new ArrayList<>();
        private int comparisons;

        void comparison() {
            comparisons++;
        }

        void add(EventType type, String title, String detail, EventTarget target) {
            events.add(new TreeAnimationEvent(
                    type, title, detail, snapshot(), target, comparisons));
        }

        void complete(String title, String detail, EventTarget target) {
            add(EventType.COMPLETE, title, detail, target);
        }

        <T> OperationTrace<T> result(T result) {
            return new OperationTrace<>(result, events, comparisons);
        }
    }
}
