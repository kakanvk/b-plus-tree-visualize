package com.bplustree.visualizer.visualization;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

/** A parent-child connector or dashed, arrow-like link between adjacent leaves. */
public final class VisualEdge extends Path {
    public enum Kind {
        PARENT_CHILD,
        LEAF_LINK
    }

    private final Kind kind;
    private final Long sourceNodeId;
    private final Long targetNodeId;

    public VisualEdge(Kind kind, double startX, double startY, double endX, double endY) {
        this(kind, null, null, startX, startY, endX, endY);
    }

    public VisualEdge(
            Kind kind,
            Long sourceNodeId,
            Long targetNodeId,
            double startX,
            double startY,
            double endX,
            double endY) {
        this.kind = kind;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        configure();
        update(startX, startY, endX, endY);
    }

    /** Creates an edge whose endpoints follow both visual nodes during layout animation. */
    public VisualEdge(Kind kind, VisualNode source, VisualNode target) {
        this.kind = kind;
        this.sourceNodeId = source.getNodeId();
        this.targetNodeId = target.getNodeId();
        configure();
        bindTo(source, target);
    }

    private void configure() {
        setMouseTransparent(true);
        getStyleClass().add(kind == Kind.LEAF_LINK ? "leaf-link" : "tree-edge");
    }

    public Kind getKind() {
        return kind;
    }

    public Long getSourceNodeId() {
        return sourceNodeId;
    }

    public Long sourceNodeId() {
        return sourceNodeId;
    }

    public Long getTargetNodeId() {
        return targetNodeId;
    }

    public Long targetNodeId() {
        return targetNodeId;
    }

    public boolean connects(long sourceId, long targetId) {
        return sourceNodeId != null
                && targetNodeId != null
                && sourceNodeId == sourceId
                && targetNodeId == targetId;
    }

    public void update(double startX, double startY, double endX, double endY) {
        getElements().clear();
        getElements().add(new MoveTo(startX, startY));
        if (kind == Kind.PARENT_CHILD) {
            double middleY = startY + (endY - startY) * 0.48;
            getElements().add(new CubicCurveTo(startX, middleY, endX, middleY, endX, endY));
            return;
        }

        getElements().add(new LineTo(endX, endY));
    }

    private void bindTo(VisualNode source, VisualNode target) {
        DoubleBinding startX;
        DoubleBinding startY;
        DoubleBinding endX;
        DoubleBinding endY;
        if (kind == Kind.PARENT_CHILD) {
            startX = coordinate(source, true, 0.5);
            startY = coordinate(source, false, 1.0);
            endX = coordinate(target, true, 0.5);
            endY = coordinate(target, false, 0.0);
        } else {
            startX = coordinate(source, true, 1.0).add(3.0);
            startY = coordinate(source, false, 0.55);
            endX = coordinate(target, true, 0.0).subtract(3.0);
            endY = coordinate(target, false, 0.55);
        }

        MoveTo move = new MoveTo();
        move.xProperty().bind(startX);
        move.yProperty().bind(startY);
        getElements().add(move);
        if (kind == Kind.PARENT_CHILD) {
            DoubleBinding middleY = startY.add(endY.subtract(startY).multiply(0.48));
            CubicCurveTo curve = new CubicCurveTo();
            curve.controlX1Property().bind(startX);
            curve.controlY1Property().bind(middleY);
            curve.controlX2Property().bind(endX);
            curve.controlY2Property().bind(middleY);
            curve.xProperty().bind(endX);
            curve.yProperty().bind(endY);
            getElements().add(curve);
        } else {
            LineTo line = new LineTo();
            line.xProperty().bind(endX);
            line.yProperty().bind(endY);
            getElements().add(line);
        }
    }

    private static DoubleBinding coordinate(VisualNode node, boolean horizontal, double sizeFactor) {
        return Bindings.createDoubleBinding(
                () -> (horizontal ? node.getLayoutX() + node.getTranslateX()
                                : node.getLayoutY() + node.getTranslateY())
                        + (horizontal ? node.getWidth() : node.getHeight()) * sizeFactor,
                node.layoutXProperty(), node.layoutYProperty(),
                node.translateXProperty(), node.translateYProperty(),
                node.widthProperty(), node.heightProperty());
    }
}
