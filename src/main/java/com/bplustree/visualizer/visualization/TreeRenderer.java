package com.bplustree.visualizer.visualization;

import com.bplustree.visualizer.event.EventTarget;
import com.bplustree.visualizer.event.EventType;
import com.bplustree.visualizer.event.TreeAnimationEvent;
import com.bplustree.visualizer.model.BPlusNode;
import com.bplustree.visualizer.model.BPlusTree;
import com.bplustree.visualizer.model.InternalNode;
import com.bplustree.visualizer.model.NodeSnapshot;
import com.bplustree.visualizer.model.TreeSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

/** Renders immutable B+ tree checkpoints on a JavaFX pane. */
public final class TreeRenderer extends Pane {
    private static final Duration MOVE_DURATION = Duration.millis(320);
    private static final Duration FADE_DURATION = Duration.millis(220);
    private static final Duration PULSE_DURATION = Duration.millis(350);

    private final TreeLayoutEngine layoutEngine;
    private final Map<Long, VisualNode> visualNodes = new LinkedHashMap<>();
    private final Map<Long, Animation> nodeAnimations = new LinkedHashMap<>();
    private final List<Animation> auxiliaryAnimations = new ArrayList<>();
    private final List<VisualEdge> edges = new ArrayList<>();

    private boolean reducedMotion;
    private long renderGeneration;
    private TreeSnapshot renderedSnapshot = TreeSnapshot.empty();
    private TreeAnimationEvent highlightedEvent;

    public TreeRenderer() {
        this(new TreeLayoutEngine());
    }

    public TreeRenderer(TreeLayoutEngine layoutEngine) {
        this.layoutEngine = Objects.requireNonNull(layoutEngine, "layoutEngine");
        getStyleClass().add("tree-canvas");
    }

    /** Compatibility adapter. Rendering itself always consumes an immutable snapshot. */
    public void render(BPlusTree tree) {
        Objects.requireNonNull(tree, "tree");
        renderSnapshot(tree.snapshot(), indexModelNodes(tree.getRoot()));
    }

    public void render(TreeSnapshot snapshot) {
        renderSnapshot(Objects.requireNonNull(snapshot, "snapshot"), Map.of());
    }

    /** Seeks directly to an event checkpoint and applies its structural target. */
    public void render(TreeAnimationEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.snapshot().rootId() != null) {
            render(event.snapshot());
        } else {
            cancelAnimations();
        }
        highlightedEvent = event;
        applyHighlights();
    }

    private void renderSnapshot(TreeSnapshot snapshot, Map<Long, BPlusNode> modelNodes) {
        Map<Long, Point2D> visiblePositions = captureVisiblePositions();
        cancelAnimations();
        long generation = renderGeneration;
        TreeLayoutEngine.LayoutResult layout = layoutEngine.layout(snapshot);
        Map<Long, VisualNode> nextVisualNodes = new LinkedHashMap<>();
        List<Node> newEdges = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();

        for (Map.Entry<Long, TreeLayoutEngine.NodePlacement> entry : layout.placements().entrySet()) {
            long nodeId = entry.getKey();
            NodeSnapshot nodeSnapshot = snapshot.node(nodeId).orElseThrow();
            TreeLayoutEngine.NodePlacement placement = entry.getValue();
            VisualNode visualNode = visualNodes.get(nodeId);
            boolean isNew = visualNode == null;
            if (isNew) {
                visualNode = new VisualNode(nodeSnapshot);
                BPlusNode modelNode = modelNodes.get(nodeId);
                if (modelNode != null) {
                    visualNode.refresh(nodeSnapshot, modelNode);
                }
            } else {
                visualNode.refresh(nodeSnapshot, modelNodes.get(nodeId));
            }

            Point2D visiblePosition = visiblePositions.getOrDefault(
                    nodeId, new Point2D(placement.x(), placement.y()));
            visualNode.relocate(placement.x(), placement.y());
            visualNode.setTranslateX(visiblePosition.getX() - placement.x());
            visualNode.setTranslateY(visiblePosition.getY() - placement.y());
            visualNode.setOpacity(isNew && !reducedMotion ? 0.0 : 1.0);

            nextVisualNodes.put(nodeId, visualNode);
            nodes.add(visualNode);
            animateNode(nodeId, visualNode, isNew, generation);
        }

        visualNodes.clear();
        visualNodes.putAll(nextVisualNodes);
        renderedSnapshot = snapshot;

        buildParentEdges(snapshot, nextVisualNodes, newEdges);
        buildLeafEdges(snapshot, nextVisualNodes, newEdges);

        edges.clear();
        for (Node node : newEdges) {
            if (node instanceof VisualEdge edge) {
                edges.add(edge);
            }
        }

        List<Node> layers = new ArrayList<>(newEdges.size() + nodes.size());
        layers.addAll(newEdges);
        layers.addAll(nodes);
        getChildren().setAll(layers);
        setMinSize(layout.width(), layout.height());
        setPrefSize(layout.width(), layout.height());
        applyHighlights();
    }

    public void highlight(TreeAnimationEvent event) {
        highlightedEvent = Objects.requireNonNull(event, "event");
        applyHighlights();
    }

    public void clearHighlights() {
        highlightedEvent = null;
        visualNodes.values().forEach(VisualNode::clearHighlight);
        edges.forEach(edge -> edge.getStyleClass().remove("active"));
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        if (reducedMotion) {
            cancelAnimations();
            for (VisualNode node : visualNodes.values()) {
                node.setTranslateX(0);
                node.setTranslateY(0);
                node.setScaleX(1);
                node.setScaleY(1);
                node.setOpacity(1);
            }
        }
    }

    public boolean isReducedMotion() {
        return reducedMotion;
    }

    public TreeLayoutEngine getLayoutEngine() {
        return layoutEngine;
    }

    public TreeSnapshot getRenderedSnapshot() {
        return renderedSnapshot;
    }

    public VisualNode getVisualNode(long nodeId) {
        return visualNodes.get(nodeId);
    }

    public VisualNode getVisualNode(BPlusNode modelNode) {
        return modelNode == null ? null : visualNodes.get(modelNode.getId());
    }

    public Map<Long, VisualNode> getVisualNodesById() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(visualNodes));
    }

    /** Compatibility view containing only visuals currently attached to live model nodes. */
    public Map<BPlusNode, VisualNode> getVisualNodes() {
        IdentityHashMap<BPlusNode, VisualNode> byModel = new IdentityHashMap<>();
        visualNodes.values().forEach(node -> {
            if (node.getModelNode() != null) {
                byModel.put(node.getModelNode(), node);
            }
        });
        return Collections.unmodifiableMap(byModel);
    }

    public List<VisualEdge> getVisualEdges() {
        return List.copyOf(edges);
    }

    private void buildParentEdges(
            TreeSnapshot snapshot,
            Map<Long, VisualNode> nodes,
            List<Node> edgeNodes) {
        for (NodeSnapshot parent : snapshot.nodes()) {
            VisualNode parentNode = nodes.get(parent.id());
            if (parentNode == null) {
                continue;
            }
            for (Long childId : parent.childIds()) {
                VisualNode childNode = nodes.get(childId);
                if (childNode != null) {
                    edgeNodes.add(new VisualEdge(
                            VisualEdge.Kind.PARENT_CHILD, parentNode, childNode));
                }
            }
        }
    }

    private void buildLeafEdges(
            TreeSnapshot snapshot,
            Map<Long, VisualNode> nodes,
            List<Node> edgeNodes) {
        for (NodeSnapshot leaf : snapshot.nodes()) {
            if (!leaf.leaf() || leaf.nextLeafId() == null) {
                continue;
            }
            VisualNode current = nodes.get(leaf.id());
            VisualNode next = nodes.get(leaf.nextLeafId());
            if (current != null && next != null) {
                edgeNodes.add(new VisualEdge(VisualEdge.Kind.LEAF_LINK, current, next));
            }
        }
    }

    private void animateNode(long nodeId, VisualNode node, boolean isNew, long generation) {
        if (reducedMotion) {
            node.setTranslateX(0);
            node.setTranslateY(0);
            node.setOpacity(1);
            return;
        }

        ParallelTransition transition = new ParallelTransition();
        if (Math.abs(node.getTranslateX()) > 0.1 || Math.abs(node.getTranslateY()) > 0.1) {
            TranslateTransition movement = new TranslateTransition(MOVE_DURATION, node);
            movement.setToX(0);
            movement.setToY(0);
            transition.getChildren().add(movement);
        }
        if (isNew) {
            FadeTransition fade = new FadeTransition(FADE_DURATION, node);
            fade.setFromValue(0);
            fade.setToValue(1);
            transition.getChildren().add(fade);
        }
        if (transition.getChildren().isEmpty()) {
            node.setTranslateX(0);
            node.setTranslateY(0);
            node.setOpacity(1);
            return;
        }

        nodeAnimations.put(nodeId, transition);
        transition.setOnFinished(event -> {
            if (generation == renderGeneration) {
                nodeAnimations.remove(nodeId, transition);
            }
        });
        transition.play();
    }

    public void pulseNode(int key) {
        if (reducedMotion) {
            return;
        }
        visualNodes.values().stream()
                .filter(node -> node.containsKey(key))
                .findFirst()
                .ifPresent(node -> {
                    long generation = renderGeneration;
                    ScaleTransition pulse = new ScaleTransition(PULSE_DURATION, node);
                    pulse.setFromX(1.0);
                    pulse.setFromY(1.0);
                    pulse.setToX(1.12);
                    pulse.setToY(1.12);
                    pulse.setCycleCount(2);
                    pulse.setAutoReverse(true);
                    auxiliaryAnimations.add(pulse);
                    pulse.setOnFinished(event -> {
                        if (generation == renderGeneration) {
                            auxiliaryAnimations.remove(pulse);
                        }
                    });
                    pulse.play();
                });
    }

    public Point2D getNodeCenter(int key) {
        return visualNodes.values().stream()
                .filter(node -> node.containsKey(key))
                .findFirst()
                .map(node -> new Point2D(
                        node.getLayoutX() + node.getTranslateX() + node.getWidth() / 2.0,
                        node.getLayoutY() + node.getTranslateY() + node.getHeight() / 2.0))
                .orElse(null);
    }

    private void applyHighlights() {
        visualNodes.values().forEach(VisualNode::clearHighlight);
        edges.forEach(edge -> edge.getStyleClass().remove("active"));
        if (highlightedEvent == null) {
            return;
        }

        EventTarget target = highlightedEvent.target();
        String focusStyle = styleFor(highlightedEvent.type());
        VisualNode focusNode = target.nodeId() == null ? null : visualNodes.get(target.nodeId());
        if (focusNode != null) {
            if (target.keyIndex() != null) {
                focusNode.setKeyHighlightStyle(target.keyIndex(), focusStyle);
            } else {
                focusNode.setHighlightStyle(focusStyle);
            }
        } else if (target.nodeId() == null && target.focusKey() != null) {
            visualNodes.values().stream()
                    .filter(node -> node.containsKey(target.focusKey()))
                    .findFirst()
                    .ifPresent(node -> node.setHighlightStyle(focusStyle));
        }

        for (Long relatedNodeId : target.relatedNodeIds()) {
            VisualNode related = visualNodes.get(relatedNodeId);
            if (related != null && related != focusNode && !hasRuntimeStyle(related)) {
                related.setHighlightStyle("active");
            }
        }

        if (target.relatedNodeIds().isEmpty() && target.nodeId() == null) {
            for (Integer relatedKey : target.relatedKeys()) {
                if (relatedKey == null || Objects.equals(relatedKey, target.focusKey())) {
                    continue;
                }
                visualNodes.values().stream()
                        .filter(node -> node.containsKey(relatedKey))
                        .filter(node -> !hasRuntimeStyle(node))
                        .forEach(node -> node.setHighlightStyle("active"));
            }
        }

        if (highlightedEvent.type() == EventType.TRAVERSE_EDGE && target.nodeId() != null) {
            for (Long relatedNodeId : target.relatedNodeIds()) {
                edges.stream()
                        .filter(edge -> edge.connects(target.nodeId(), relatedNodeId))
                        .forEach(edge -> edge.getStyleClass().add("active"));
            }
        }
    }

    private static String styleFor(EventType type) {
        return switch (type) {
            case NODE_OVERFLOW, SPLIT_NODE, PROMOTE_KEY, CREATE_ROOT -> "split";
            case DELETE_KEY, NODE_UNDERFLOW, MERGE_NODE, BORROW_KEY, SHRINK_ROOT, ERROR -> "merge";
            case MATCH_KEY, HIGHLIGHT_RANGE, COMPLETE -> "result";
            case VISIT_NODE, COMPARE_KEY, TRAVERSE_EDGE -> "searching";
            case INSERT_KEY, UPDATE_SEPARATOR -> "active";
        };
    }

    private static boolean hasRuntimeStyle(VisualNode node) {
        return node.getStyleClass().stream().anyMatch(
                style -> style.equals("active")
                        || style.equals("searching")
                        || style.equals("split")
                        || style.equals("merge")
                        || style.equals("result"));
    }

    private Map<Long, Point2D> captureVisiblePositions() {
        Map<Long, Point2D> positions = new LinkedHashMap<>();
        visualNodes.forEach((id, node) -> positions.put(id, new Point2D(
                node.getLayoutX() + node.getTranslateX(),
                node.getLayoutY() + node.getTranslateY())));
        return positions;
    }

    private void cancelAnimations() {
        renderGeneration++;
        nodeAnimations.values().forEach(Animation::stop);
        auxiliaryAnimations.forEach(Animation::stop);
        nodeAnimations.clear();
        auxiliaryAnimations.clear();
        visualNodes.values().forEach(node -> {
            node.setScaleX(1);
            node.setScaleY(1);
        });
    }

    private static Map<Long, BPlusNode> indexModelNodes(BPlusNode root) {
        Map<Long, BPlusNode> nodes = new LinkedHashMap<>();
        List<BPlusNode> pending = new ArrayList<>();
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
}
