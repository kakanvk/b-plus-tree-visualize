package com.bplustree.visualizer.visualization;

import com.bplustree.visualizer.model.BPlusNode;
import com.bplustree.visualizer.model.BPlusTree;
import com.bplustree.visualizer.model.InternalNode;
import com.bplustree.visualizer.model.NodeSnapshot;
import com.bplustree.visualizer.model.TreeSnapshot;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Computes stable, non-overlapping top-left positions for a B+ tree snapshot. */
public final class TreeLayoutEngine {
    public static final double DEFAULT_LEVEL_GAP = 105.0;
    public static final double DEFAULT_SIBLING_GAP = 28.0;
    public static final double DEFAULT_PADDING = 32.0;

    private final double levelGap;
    private final double siblingGap;
    private final double padding;

    public TreeLayoutEngine() {
        this(DEFAULT_LEVEL_GAP, DEFAULT_SIBLING_GAP, DEFAULT_PADDING);
    }

    public TreeLayoutEngine(double levelGap, double siblingGap, double padding) {
        if (levelGap <= 0 || siblingGap < 0 || padding < 0) {
            throw new IllegalArgumentException("Layout gaps and padding must be non-negative");
        }
        this.levelGap = levelGap;
        this.siblingGap = siblingGap;
        this.padding = padding;
    }

    public LayoutResult layout(BPlusTree tree) {
        Objects.requireNonNull(tree, "tree");
        LayoutResult result = layout(tree.snapshot());
        return result.withModelNodes(indexModelNodes(tree.getRoot()));
    }

    /** Compatibility overload for callers that only have a live model root. */
    public LayoutResult layout(BPlusNode root) {
        Objects.requireNonNull(root, "root");
        Map<Long, BPlusNode> modelNodes = indexModelNodes(root);
        Map<Long, NodeSnapshot> snapshots = new LinkedHashMap<>();
        for (BPlusNode node : modelNodes.values()) {
            var childIds = node instanceof InternalNode internal
                    ? internal.getChildren().stream().map(BPlusNode::getId).toList()
                    : java.util.List.<Long>of();
            snapshots.put(node.getId(), new NodeSnapshot(
                    node.getId(), node.isLeaf(), node.getKeys(), childIds,
                    node.getParent() == null ? null : node.getParent().getId(), null, null));
        }
        TreeSnapshot snapshot = new TreeSnapshot(
                0, 0, root.getId(), java.util.List.copyOf(snapshots.values()));
        return layout(snapshot).withModelNodes(modelNodes);
    }

    /** Lays out immutable nodes by their stable IDs, including transient key counts. */
    public LayoutResult layout(TreeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.rootId() == null) {
            return new LayoutResult(Map.of(), Map.of(), padding * 2, padding * 2);
        }

        NodeSnapshot root = snapshot.root().orElseThrow();
        Map<Long, Double> widths = new LinkedHashMap<>();
        double treeWidth = measure(root, snapshot, widths, new java.util.HashSet<>());
        Map<Long, NodePlacement> placements = new LinkedHashMap<>();
        int maximumDepth = place(root, snapshot, padding, 0, widths, placements,
                new java.util.HashSet<>());
        double width = treeWidth + padding * 2;
        double height = padding * 2 + maximumDepth * levelGap + VisualNode.NODE_HEIGHT;
        return new LayoutResult(placements, Map.of(), width, height);
    }

    private double measure(
            NodeSnapshot node,
            TreeSnapshot snapshot,
            Map<Long, Double> widths,
            java.util.Set<Long> visiting) {
        Double measured = widths.get(node.id());
        if (measured != null) {
            return measured;
        }
        if (!visiting.add(node.id())) {
            throw new IllegalArgumentException("Cycle in snapshot at node " + node.id());
        }

        double nodeWidth = VisualNode.widthFor(node);
        double childrenWidth = 0;
        int childCount = 0;
        for (Long childId : node.childIds()) {
            NodeSnapshot child = requireNode(snapshot, childId, node.id());
            childrenWidth += measure(child, snapshot, widths, visiting);
            childCount++;
        }
        if (childCount > 1) {
            childrenWidth += siblingGap * (childCount - 1);
        }

        double subtreeWidth = Math.max(nodeWidth, childrenWidth);
        widths.put(node.id(), subtreeWidth);
        visiting.remove(node.id());
        return subtreeWidth;
    }

    private int place(
            NodeSnapshot node,
            TreeSnapshot snapshot,
            double left,
            int depth,
            Map<Long, Double> widths,
            Map<Long, NodePlacement> placements,
            java.util.Set<Long> placed) {
        if (!placed.add(node.id())) {
            throw new IllegalArgumentException("Node " + node.id() + " has multiple parents");
        }

        double subtreeWidth = widths.get(node.id());
        double nodeWidth = VisualNode.widthFor(node);
        placements.put(node.id(), new NodePlacement(
                left + (subtreeWidth - nodeWidth) / 2.0,
                padding + depth * levelGap,
                nodeWidth,
                VisualNode.NODE_HEIGHT,
                depth,
                subtreeWidth));

        if (node.childIds().isEmpty()) {
            return depth;
        }

        double childrenWidth = node.childIds().stream()
                .mapToDouble(widths::get)
                .sum() + siblingGap * (node.childIds().size() - 1);
        double cursor = left + (subtreeWidth - childrenWidth) / 2.0;
        int maximumDepth = depth;
        for (Long childId : node.childIds()) {
            NodeSnapshot child = requireNode(snapshot, childId, node.id());
            maximumDepth = Math.max(maximumDepth,
                    place(child, snapshot, cursor, depth + 1, widths, placements, placed));
            cursor += widths.get(childId) + siblingGap;
        }
        return maximumDepth;
    }

    private static NodeSnapshot requireNode(TreeSnapshot snapshot, long childId, long parentId) {
        return snapshot.node(childId).orElseThrow(() -> new IllegalArgumentException(
                "Node " + parentId + " references missing child " + childId));
    }

    private static Map<Long, BPlusNode> indexModelNodes(BPlusNode root) {
        Map<Long, BPlusNode> nodes = new LinkedHashMap<>();
        java.util.List<BPlusNode> pending = new java.util.ArrayList<>();
        pending.add(root);
        for (int index = 0; index < pending.size(); index++) {
            BPlusNode node = pending.get(index);
            if (nodes.putIfAbsent(node.getId(), node) != null) {
                continue;
            }
            if (node instanceof InternalNode internal) {
                pending.addAll(internal.getChildren());
            }
        }
        return nodes;
    }

    public double getLevelGap() {
        return levelGap;
    }

    public double getSiblingGap() {
        return siblingGap;
    }

    public double getPadding() {
        return padding;
    }

    public record NodePlacement(
            double x,
            double y,
            double width,
            double height,
            int depth,
            double subtreeWidth) {
    }

    public static final class LayoutResult {
        private final Map<Long, NodePlacement> placements;
        private final Map<BPlusNode, NodePlacement> modelPlacements;
        private final double width;
        private final double height;

        private LayoutResult(
                Map<Long, NodePlacement> placements,
                Map<BPlusNode, NodePlacement> modelPlacements,
                double width,
                double height) {
            this.placements = Collections.unmodifiableMap(new LinkedHashMap<>(placements));
            IdentityHashMap<BPlusNode, NodePlacement> identityPlacements = new IdentityHashMap<>();
            identityPlacements.putAll(modelPlacements);
            this.modelPlacements = Collections.unmodifiableMap(identityPlacements);
            this.width = width;
            this.height = height;
        }

        private LayoutResult withModelNodes(Map<Long, BPlusNode> modelNodes) {
            IdentityHashMap<BPlusNode, NodePlacement> byModel = new IdentityHashMap<>();
            modelNodes.forEach((id, node) -> {
                NodePlacement placement = placements.get(id);
                if (placement != null) {
                    byModel.put(node, placement);
                }
            });
            return new LayoutResult(placements, byModel, width, height);
        }

        public Map<Long, NodePlacement> placements() {
            return placements;
        }

        public NodePlacement placementOf(long nodeId) {
            return placements.get(nodeId);
        }

        /** Compatibility lookup for live model callers. */
        public NodePlacement placementOf(BPlusNode node) {
            if (node == null) {
                return null;
            }
            NodePlacement placement = modelPlacements.get(node);
            return placement != null ? placement : placements.get(node.getId());
        }

        /** Compatibility view populated by live-model layout overloads. */
        public Map<BPlusNode, NodePlacement> modelPlacements() {
            return modelPlacements;
        }

        public double width() {
            return width;
        }

        public double height() {
            return height;
        }
    }
}
