package com.bplustree.visualizer.visualization;

import com.bplustree.visualizer.model.BPlusNode;
import com.bplustree.visualizer.model.InternalNode;
import com.bplustree.visualizer.model.NodeSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** JavaFX representation of one immutable node snapshot and its key cells. */
public final class VisualNode extends VBox {
    public static final double KEY_CELL_WIDTH = 38.0;
    private static final double KEY_CHARACTER_WIDTH = 10.0;
    private static final double KEY_HORIZONTAL_PADDING = 30.0;
    public static final double MIN_NODE_WIDTH = 54.0;
    public static final double NODE_HEIGHT = 54.0;

    private static final List<String> RUNTIME_CLASSES =
            List.of("active", "searching", "split", "merge", "result");

    private final long nodeId;
    private final HBox keyRow = new HBox();
    private final List<Label> keyCells = new ArrayList<>();
    private NodeSnapshot snapshot;
    private BPlusNode modelNode;
    private List<Integer> displayedKeys = List.of();

    public VisualNode(NodeSnapshot snapshot) {
        this(snapshot, null);
    }

    /** Compatibility constructor; display state is still copied into a snapshot. */
    public VisualNode(BPlusNode modelNode) {
        this(snapshotOf(Objects.requireNonNull(modelNode, "modelNode")), modelNode);
    }

    private VisualNode(NodeSnapshot snapshot, BPlusNode modelNode) {
        NodeSnapshot initial = Objects.requireNonNull(snapshot, "snapshot");
        this.nodeId = initial.id();
        this.modelNode = modelNode;
        setAlignment(Pos.CENTER);
        setMinHeight(NODE_HEIGHT);
        setPrefHeight(NODE_HEIGHT);
        setMaxHeight(NODE_HEIGHT);
        getStyleClass().add("visual-node");

        keyRow.setAlignment(Pos.CENTER);
        getChildren().add(keyRow);
        refreshInternal(initial, modelNode);
    }

    public long getNodeId() {
        return nodeId;
    }

    public long nodeId() {
        return nodeId;
    }

    public NodeSnapshot getSnapshot() {
        return snapshot;
    }

    public NodeSnapshot snapshot() {
        return snapshot;
    }

    /** May be null when this visual was rendered directly from a snapshot. */
    public BPlusNode getModelNode() {
        return modelNode;
    }

    public List<Label> getKeyCells() {
        return Collections.unmodifiableList(keyCells);
    }

    /** Refreshes compatibility visuals from their live model, if one is attached. */
    public void refresh() {
        if (modelNode != null) {
            refreshInternal(snapshotOf(modelNode), modelNode);
        }
    }

    public void refresh(NodeSnapshot snapshot) {
        refreshInternal(snapshot, null);
    }

    void refresh(NodeSnapshot snapshot, BPlusNode modelNode) {
        refreshInternal(snapshot, modelNode);
    }

    private void refreshInternal(NodeSnapshot updated, BPlusNode attachedModel) {
        Objects.requireNonNull(updated, "snapshot");
        if (updated.id() != nodeId) {
            throw new IllegalArgumentException(
                    "Cannot refresh node " + nodeId + " from snapshot " + updated.id());
        }
        snapshot = updated;
        modelNode = attachedModel;
        getStyleClass().removeAll("leaf", "internal");
        getStyleClass().add(updated.leaf() ? "leaf" : "internal");

        List<Integer> keys = updated.keys();
        double width = widthFor(updated);
        setMinWidth(width);
        setPrefWidth(width);
        setMaxWidth(width);
        if (keys.equals(displayedKeys) && !keyCells.isEmpty()) {
            return;
        }

        displayedKeys = keys;
        keyCells.clear();
        keyRow.getChildren().clear();
        if (keys.isEmpty()) {
            Label empty = createCell("∅");
            empty.getStyleClass().add("empty");
            keyCells.add(empty);
            keyRow.getChildren().add(empty);
            return;
        }
        for (int index = 0; index < keys.size(); index++) {
            Label cell = createCell(Integer.toString(keys.get(index)));
            if (index == keys.size() - 1) {
                cell.getStyleClass().add("last");
            }
            keyCells.add(cell);
            keyRow.getChildren().add(cell);
        }
    }

    public boolean containsKey(int key) {
        return snapshot.keys().contains(key);
    }

    public void setHighlightStyle(String styleClass) {
        clearHighlight();
        addRuntimeClass(this, styleClass);
    }

    /** Marks exactly one key cell while retaining a visible node-level highlight. */
    public void setKeyHighlightStyle(int keyIndex, String styleClass) {
        setHighlightStyle(styleClass);
        if (keyIndex >= 0 && keyIndex < snapshot.keys().size()) {
            addRuntimeClass(keyCells.get(keyIndex), styleClass);
        }
    }

    public void clearHighlight() {
        getStyleClass().removeAll(RUNTIME_CLASSES);
        keyCells.forEach(cell -> cell.getStyleClass().removeAll(RUNTIME_CLASSES));
    }

    public static double widthFor(BPlusNode node) {
        Objects.requireNonNull(node, "node");
        return widthForKeys(node.getKeys());
    }

    public static double widthFor(NodeSnapshot node) {
        Objects.requireNonNull(node, "node");
        return widthForKeys(node.keys());
    }

    private static double widthForKeys(List<Integer> keys) {
        double keysWidth = keys.isEmpty()
                ? KEY_CELL_WIDTH
                : keys.stream().mapToDouble(key -> cellWidth(Integer.toString(key))).sum();
        return Math.max(MIN_NODE_WIDTH, keysWidth);
    }

    private Label createCell(String text) {
        Label cell = new Label(text);
        double width = cellWidth(text);
        cell.getStyleClass().add("key-cell");
        cell.setAlignment(Pos.CENTER);
        cell.setTextOverrun(OverrunStyle.CLIP);
        cell.setMinWidth(width);
        cell.setPrefWidth(width);
        cell.setMaxWidth(width);
        HBox.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }

    private static void addRuntimeClass(javafx.scene.Node node, String styleClass) {
        if (styleClass != null && RUNTIME_CLASSES.contains(styleClass)) {
            node.getStyleClass().add(styleClass);
        }
    }

    private static NodeSnapshot snapshotOf(BPlusNode node) {
        List<Long> childIds = node instanceof InternalNode internal
                ? internal.getChildren().stream().map(BPlusNode::getId).toList()
                : List.of();
        return new NodeSnapshot(
                node.getId(), node.isLeaf(), node.getKeys(), childIds,
                node.getParent() == null ? null : node.getParent().getId(), null, null);
    }

    private static double cellWidth(String text) {
        return Math.max(KEY_CELL_WIDTH, text.length() * KEY_CHARACTER_WIDTH + KEY_HORIZONTAL_PADDING);
    }
}
