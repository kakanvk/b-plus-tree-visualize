package com.bplustree.visualizer.controller;

import com.bplustree.visualizer.event.EventType;
import com.bplustree.visualizer.event.OperationResult;
import com.bplustree.visualizer.event.TreeAnimationEvent;
import com.bplustree.visualizer.model.BPlusTreeStatistics;
import com.bplustree.visualizer.model.NodeSnapshot;
import com.bplustree.visualizer.model.TreeSnapshot;
import com.bplustree.visualizer.service.BPlusTreeService;
import com.bplustree.visualizer.visualization.AnimationManager;
import com.bplustree.visualizer.visualization.TreeRenderer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.util.Duration;

/** Builds the application shell and coordinates user interactions. */
public final class MainController {

    private static final int DEFAULT_ORDER = 4;
    private static final String ICON_PLUS = "M8 2 V14 M2 8 H14";
    private static final String ICON_SEARCH =
        "M11.5 11.5 L15 15 M13 7 A6 6 0 1 1 1 7 A6 6 0 0 1 13 7";
    private static final String ICON_DELETE =
        "M3 4 H13 M5 4 V2 H11 V4 M5 6 V13 H11 V6";
    private static final String ICON_LOGO =
        "M2 8 H14 M2 8 L5 5 M2 8 L5 11 M14 8 L11 5 M14 8 L11 11";
    private static final String ICON_SAMPLE =
        "M2 2 H7 V7 H2 Z M9 2 H14 V7 H9 Z M2 9 H7 V14 H2 Z M9 9 H14 V14 H9 Z";
    private static final String ICON_RESET =
        "M3 6 A5.5 5.5 0 1 1 4 12 M3 2 V6 H7";
    private static final String ICON_BALANCE =
        "M2 5 H12 M9 2 L12 5 L9 8 M14 11 H4 M7 8 L4 11 L7 14";
    private static final String ICON_PLAY = "M4 2 L14 8 L4 14 Z";
    private static final String ICON_PAUSE =
        "M4 3 H7 V13 H4 Z M9 3 H12 V13 H9 Z";
    private static final String ICON_PREVIOUS = "M10 3 L5 8 L10 13";
    private static final String ICON_NEXT = "M6 3 L11 8 L6 13";

    private final BPlusTreeService service = new BPlusTreeService(
        DEFAULT_ORDER
    );
    private final TreeRenderer renderer = new TreeRenderer();
    private final AnimationManager animationManager = new AnimationManager(
        this::showAnimationEvent
    );
    private final StackPane view = new StackPane();
    private final VBox stepsBox = new VBox(2);
    private final ScrollPane stepsScroll = new ScrollPane(stepsBox);
    private Label stepsPlaceholder;
    private final Map<String, Label> statisticValues = new LinkedHashMap<>();
    private final VBox emptyContent = new VBox();
    private final Label toast = new Label();
    private final ComboBox<Integer> orderSelector = new ComboBox<>();
    private final TextField valueInput = integerField("Nhập khóa");
    private final List<javafx.scene.control.Control> operationControls =
        new ArrayList<>();
    private Animation toastAnimation;
    private int lastComparisons;
    private List<Integer> insertionOrder = new ArrayList<>();

    public MainController() {
        configureOrderSelector();

        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setTop(buildHeader());
        shell.setCenter(buildWorkspace());
        shell.setBottom(buildAnimationBar());

        toast.getStyleClass().addAll("toast", "toast-success");
        toast.setVisible(false);
        toast.setManaged(false);
        toast.setMaxWidth(420);
        StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toast, new Insets(0, 24, 22, 24));
        view.getChildren().addAll(shell, toast);

        configureAnimationBindings();
        renderer.render(service.getTree());
        updateInformation();
        updateEmptyState();
        stepsScroll.setVisible(false);
        stepsScroll.setManaged(false);
    }

    public Parent getView() {
        return view;
    }

    private VBox buildHeader() {
        SVGPath logoGlyph = icon(ICON_LOGO);
        logoGlyph.getStyleClass().add("brand-glyph");
        StackPane mark = new StackPane(logoGlyph);
        mark.getStyleClass().add("brand-mark");
        Label title = new Label("Trực quan hóa cây B+");
        title.getStyleClass().add("app-title");

        orderSelector.getStyleClass().add("order-selector");
        HBox left = new HBox(12, mark, title, orderSelector);
        left.setAlignment(Pos.CENTER_LEFT);
        left.getStyleClass().add("toolbar-identity");

        valueInput.setPrefWidth(116);
        valueInput.getStyleClass().add("inline-key-input");
        valueInput.setOnAction(event -> insertInline());
        Button insert = actionButton(
            "Thêm khóa",
            ICON_PLUS,
            "primary-button",
            this::insertInline
        );
        Button delete = actionButton(
            "Xóa",
            ICON_DELETE,
            "secondary-button",
            this::showDeleteDialog
        );
        Button search = actionButton(
            "Tìm kiếm",
            ICON_SEARCH,
            "secondary-button",
            this::showSearchDialog
        );
        MenuButton samples = buildSamplesMenu();
        Button balance = actionButton(
            "Cân bằng",
            ICON_BALANCE,
            "secondary-button",
            this::rebalanceTree
        );
        Button reset = actionButton(
            "Đặt lại",
            ICON_RESET,
            "ghost-button",
            this::resetTree
        );

        FlowPane actions = new FlowPane(
            Orientation.HORIZONTAL,
            8,
            8,
            valueInput,
            insert,
            delete,
            search,
            samples,
            balance,
            reset
        );
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPrefWrapLength(760);
        actions.setMinWidth(0);
        actions.setMaxWidth(Double.MAX_VALUE);
        actions.getStyleClass().add("toolbar-actions");
        BorderPane toolbar = new BorderPane();
        toolbar.setLeft(left);
        toolbar.setCenter(actions);
        toolbar.setMinWidth(0);
        BorderPane.setAlignment(actions, Pos.CENTER_RIGHT);
        toolbar.getStyleClass().add("action-toolbar");
        operationControls.addAll(
            List.of(
                orderSelector,
                valueInput,
                insert,
                delete,
                search,
                samples,
                balance,
                reset
            )
        );

        VBox header = new VBox(toolbar);
        header.getStyleClass().add("header-area");
        return header;
    }

    private SplitPane buildWorkspace() {
        BorderPane treePanel = buildTreePanel();
        VBox sidebar = buildSidebar();

        SplitPane workspace = new SplitPane(treePanel, sidebar);
        workspace.setDividerPositions(0.74);
        workspace.setMinHeight(0);
        workspace.getStyleClass().add("workspace-split");
        SplitPane.setResizableWithParent(sidebar, false);
        return workspace;
    }

    private BorderPane buildTreePanel() {
        Label canvasTitle = new Label("Cấu trúc cây");
        canvasTitle.getStyleClass().add("section-title");
        Label caption = new Label(
            "Đường liền: nút con    Đường nét đứt: liên kết nút lá"
        );
        caption.getStyleClass().add("canvas-caption");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox canvasHeader = new HBox(12, canvasTitle, headerSpacer, caption);
        canvasHeader.setAlignment(Pos.CENTER_LEFT);
        canvasHeader.getStyleClass().add("canvas-header");

        StackPane treeSurface = new StackPane(renderer);
        treeSurface.getStyleClass().add("tree-canvas");
        StackPane.setAlignment(renderer, Pos.TOP_CENTER);
        ScrollPane scrollPane = new ScrollPane(treeSurface);
        scrollPane.getStyleClass().add("tree-scroll");
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        StackPane canvasStack = new StackPane(scrollPane, buildEmptyState());
        BorderPane treePanel = new BorderPane(canvasStack);
        treePanel.setTop(canvasHeader);
        treePanel.setMinWidth(580);
        treePanel.setMinHeight(0);
        treePanel.getStyleClass().add("tree-panel");
        return treePanel;
    }

    private VBox buildEmptyState() {
        SVGPath treeGlyph = icon(
            "M8 1 V14 M8 4 L3 8 M8 4 L13 8 M3 8 V13 M13 8 V13 M1 13 H5 M11 13 H15"
        );
        treeGlyph.getStyleClass().add("empty-glyph");
        StackPane illustration = new StackPane(treeGlyph);
        illustration.getStyleClass().add("empty-illustration");
        Label title = new Label("Cây B+ chưa có dữ liệu");
        title.getStyleClass().add("empty-title");
        Label copy = new Label(
            "Thêm một khóa hoặc sử dụng dữ liệu mẫu để bắt đầu."
        );
        copy.getStyleClass().add("empty-copy");
        Button insert = actionButton(
            "Thêm khóa",
            ICON_PLUS,
            "primary-button",
            this::focusInsertInput
        );
        Button sample = actionButton(
            "Dữ liệu mẫu",
            ICON_SAMPLE,
            "secondary-button",
            () ->
                loadPreset(
                    "Dữ liệu mẫu nhỏ",
                    List.of(8, 18, 5, 15, 25, 2, 12, 20)
                )
        );
        operationControls.addAll(List.of(insert, sample));
        HBox actions = new HBox(8, insert, sample);
        actions.setAlignment(Pos.CENTER);
        emptyContent.getChildren().setAll(illustration, title, copy, actions);
        emptyContent.setSpacing(10);
        emptyContent.setAlignment(Pos.CENTER);
        emptyContent.getStyleClass().add("empty-state");
        return emptyContent;
    }

    private VBox buildSidebar() {
        Label stepsTitle = new Label("Các bước thuật toán");
        stepsTitle.getStyleClass().add("section-title");
        Label stepsHint = new Label(
            "Theo dõi quyết định của thuật toán theo từng bước."
        );
        stepsHint.getStyleClass().add("section-caption");
        stepsPlaceholder = new Label("Thực hiện một thao tác để xem các bước.");
        stepsPlaceholder.getStyleClass().add("section-caption");
        stepsPlaceholder.setWrapText(true);
        stepsScroll.getStyleClass().add("step-scroll");
        stepsScroll.setFitToWidth(true);
        stepsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        stepsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        stepsBox.getStyleClass().add("step-list");
        stepsBox.setFillWidth(true);
        VBox.setVgrow(stepsScroll, Priority.ALWAYS);
        VBox stepSection = new VBox(
            4,
            stepsTitle,
            stepsHint,
            stepsScroll,
            stepsPlaceholder
        );
        stepSection.getStyleClass().add("sidebar-section");

        GridPane statistics = new GridPane();
        statistics.getStyleClass().add("statistics-grid");
        statistics.setHgap(8);
        statistics.setVgap(8);
        ColumnConstraints firstColumn = new ColumnConstraints();
        firstColumn.setPercentWidth(50);
        ColumnConstraints secondColumn = new ColumnConstraints();
        secondColumn.setPercentWidth(50);
        statistics.getColumnConstraints().addAll(firstColumn, secondColumn);
        addStatistic(statistics, 0, 0, "Chiều cao", "height");
        addStatistic(statistics, 1, 0, "Tổng số khóa", "keys");
        addStatistic(statistics, 0, 1, "Tổng số nút", "nodes");
        addStatistic(statistics, 1, 1, "Số phép so sánh", "comparisons");
        addStatistic(statistics, 0, 2, "Nút trong", "internal");
        addStatistic(statistics, 1, 2, "Nút lá", "leaf");

        Label infoTitle = new Label("Thông tin cây");
        infoTitle.getStyleClass().add("section-title");
        VBox infoSection = new VBox(10, infoTitle, statistics);
        infoSection.getStyleClass().add("sidebar-section");

        VBox.setVgrow(stepSection, Priority.ALWAYS);
        VBox sidebar = new VBox(stepSection, new Separator(), infoSection);
        sidebar.setMinWidth(280);
        sidebar.setPrefWidth(330);
        sidebar.setMinHeight(0);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private BorderPane buildAnimationBar() {
        Button previous = iconButton(
            ICON_PREVIOUS,
            "Bước trước",
            animationManager::previous
        );
        Button play = actionButton(
            "Phát",
            ICON_PLAY,
            "play-button",
            animationManager::play
        );
        play.visibleProperty().bind(animationManager.playingProperty().not());
        play.managedProperty().bind(animationManager.playingProperty().not());
        Button pause = actionButton(
            "Tạm dừng",
            ICON_PAUSE,
            "play-button",
            animationManager::pause
        );
        pause.visibleProperty().bind(animationManager.playingProperty());
        pause.managedProperty().bind(animationManager.playingProperty());
        Button next = iconButton(
            ICON_NEXT,
            "Bước tiếp theo",
            animationManager::next
        );
        Button restart = actionButton(
            "Chạy lại",
            ICON_RESET,
            "animation-secondary",
            animationManager::replay
        );
        previous.disableProperty().bind(
            Bindings.createBooleanBinding(
                () -> animationManager.getEventCount() == 0 ||
                    animationManager.getCurrentIndex() <= 0,
                animationManager.eventCountProperty(),
                animationManager.currentIndexProperty()
            )
        );
        next.disableProperty().bind(
            Bindings.createBooleanBinding(
                () -> animationManager.getEventCount() == 0 ||
                    animationManager.getCurrentIndex() >= animationManager.getEventCount() - 1,
                animationManager.eventCountProperty(),
                animationManager.currentIndexProperty()
            )
        );
        play.disableProperty().bind(animationManager.eventCountProperty().isEqualTo(0));
        restart.disableProperty().bind(animationManager.eventCountProperty().isEqualTo(0));
        HBox playback = new HBox(6, previous, play, pause, next, restart);
        playback.setAlignment(Pos.CENTER_LEFT);

        Slider speed = new Slider(0.55, 2.0, 1.0);
        speed.setPrefWidth(132);
        animationManager.speedMultiplierProperty().bind(speed.valueProperty());
        Label speedLabel = new Label("Tốc độ mô phỏng");
        speedLabel.getStyleClass().add("animation-label");
        Label slow = new Label("Chậm");
        slow.getStyleClass().add("animation-muted");
        Label fast = new Label("Nhanh");
        fast.getStyleClass().add("animation-muted");
        HBox speedBox = new HBox(8, speedLabel, slow, speed, fast);
        speedBox.setAlignment(Pos.CENTER);

        Label status = new Label();
        status.getStyleClass().add("animation-status");
        status
            .textProperty()
            .bind(
                Bindings.createStringBinding(
                    () ->
                        animationManager.getEventCount() == 0
                            ? "Chưa có mô phỏng"
                            : "Bước " +
                              Math.max(
                                  0,
                                  animationManager.getCurrentIndex() + 1
                              ) +
                              " / " +
                              animationManager.getEventCount(),
                    animationManager.currentIndexProperty(),
                    animationManager.eventCountProperty()
                )
            );

        BorderPane bar = new BorderPane();
        bar.setLeft(playback);
        bar.setCenter(speedBox);
        bar.setRight(status);
        BorderPane.setAlignment(playback, Pos.CENTER_LEFT);
        BorderPane.setAlignment(status, Pos.CENTER_RIGHT);
        bar.getStyleClass().add("animation-bar");
        return bar;
    }

    private void configureOrderSelector() {
        orderSelector.setItems(
            FXCollections.observableArrayList(3, 4, 5, 6, 7, 8)
        );
        orderSelector
            .getSelectionModel()
            .select(Integer.valueOf(DEFAULT_ORDER));
        orderSelector.setButtonCell(
            new ListCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(
                        empty || item == null ? "Bậc cây" : "Bậc cây: " + item
                    );
                }
            }
        );
        orderSelector.setCellFactory(ignored ->
            new ListCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : "Bậc " + item);
                }
            }
        );
        orderSelector
            .valueProperty()
            .addListener((observable, oldValue, newValue) -> {
                if (
                    oldValue != null &&
                    newValue != null &&
                    !oldValue.equals(newValue)
                ) {
                    List<Integer> keys = new ArrayList<>(insertionOrder);
                    if (!new LinkedHashSet<>(keys).equals(
                            new LinkedHashSet<>(service.getTree().keys()))) {
                        keys = service.getTree().keys();
                    }
                    service.reset(newValue);
                    keys.forEach(k -> service.getTree().insert(k));
                    lastComparisons = 0;
                    clearTimelineAndRenderLive();
                    showToast(
                        "Đã chuyển sang bậc " + newValue + " với " + keys.size() + " khóa",
                        false
                    );
                }
            });
    }

    private void configureAnimationBindings() {
        var operationsDisabled = animationManager
            .playingProperty()
            .or(animationManager.atEndProperty().not());
        for (javafx.scene.control.Control control : operationControls) {
            control.disableProperty().bind(operationsDisabled);
        }
        animationManager
            .currentIndexProperty()
            .addListener((observable, oldValue, newValue) -> {
                int index = newValue.intValue();
                refreshStepHighlights();
                if (index >= 0) {
                    Platform.runLater(() -> {
                        if (index < stepsBox.getChildren().size()) {
                            Node node = stepsBox.getChildren().get(index);
                            double nodeTop = node.getBoundsInParent().getMinY();
                            double scrollHeight = stepsScroll
                                .getViewportBounds()
                                .getHeight();
                            double contentHeight = stepsBox.getHeight();
                            double target = Math.max(
                                0,
                                nodeTop - scrollHeight / 3
                            );
                            double maxScroll = Math.max(
                                0,
                                contentHeight - scrollHeight
                            );
                            stepsScroll.setVvalue(
                                Math.min(target / Math.max(1, maxScroll), 1.0)
                            );
                        }
                    });
                }
            });
    }


    private void refreshStepHighlights() {
        int current = animationManager.getCurrentIndex();
        for (int i = 0; i < stepsBox.getChildren().size(); i++) {
            Node child = stepsBox.getChildren().get(i);
            if (child instanceof HBox row) {
                row.getStyleClass().removeAll(
                    "step-completed",
                    "step-active",
                    "step-pending"
                );
                if (i < current) {
                    row.getStyleClass().add("step-completed");
                } else if (i == current) {
                    row.getStyleClass().add("step-active");
                } else {
                    row.getStyleClass().add("step-pending");
                }
                for (Node sub : row.getChildren()) {
                    if (
                        sub instanceof Label indicator &&
                        indicator
                            .getStyleClass()
                            .stream()
                            .anyMatch(c -> c.startsWith("step-indicator-"))
                    ) {
                        indicator
                            .getStyleClass()
                            .removeAll(
                                "step-indicator-completed",
                                "step-indicator-active",
                                "step-indicator-pending"
                            );
                        String state;
                        if (i < current) {
                            state = "completed";
                        } else if (i == current) {
                            state = "active";
                        } else {
                            state = "pending";
                        }
                        indicator
                            .getStyleClass()
                            .add("step-indicator-" + state);
                        indicator.setText(
                            stepIndicatorText(
                                animationManager.getEvents().get(i),
                                state
                            )
                        );
                    }
                }
            }
        }
    }

    private void insertInline() {
        String text = valueInput.getText().trim();
        if (text.isEmpty() || "-".equals(text)) {
            showToast("Vui lòng nhập khóa cần thêm", true);
            focusInsertInput();
            return;
        }
        try {
            int value = Integer.parseInt(text);
            OperationResult result = service.insert(value);
            if (result.success()) {
                insertionOrder.add(value);
            }
            runOperation(result);
            valueInput.clear();
        } catch (NumberFormatException exception) {
            showToast("Khóa nằm ngoài phạm vi số nguyên hợp lệ", true);
            valueInput.selectAll();
            focusInsertInput();
        }
    }

    private void focusInsertInput() {
        valueInput.requestFocus();
    }

    private void showDeleteDialog() {
        if (service.getTree().isEmpty()) {
            showToast("Không thể xóa vì cây đang trống", true);
            return;
        }
        showSingleValueDialog(
            "Xóa khóa",
            "Giá trị cần xóa",
            "Nhập khóa hiện có",
            "Xóa khóa",
            true,
            this::runDeleteByKey
        );
    }

    private void runDeleteByKey(int key) {
        OperationResult result = service.delete(key);
        if (result.success()) {
            insertionOrder.remove(Integer.valueOf(key));
        }
        runOperation(result);
    }

    private void showSearchDialog() {
        if (service.getTree().isEmpty()) {
            showToast("Cây đang trống, chưa có khóa để tìm kiếm", true);
            return;
        }
        showSingleValueDialog(
            "Tìm kiếm khóa",
            "Giá trị cần tìm",
            "Ví dụ: 63",
            "Tìm kiếm",
            false,
            value -> runOperation(service.search(value))
        );
    }

    private void showSingleValueDialog(
        String title,
        String fieldLabel,
        String prompt,
        String actionText,
        boolean danger,
        Consumer<Integer> action
    ) {
        Dialog<Integer> dialog = createDialog(title);
        TextField input = integerField(prompt);
        Label label = controlLabel(fieldLabel);
        Label error = validationLabel();
        VBox content = new VBox(8, label, input, error);
        dialog.getDialogPane().setContent(content);
        ButtonType submitType = addDialogButtons(dialog, actionText, danger);
        Button submit = (Button) dialog
            .getDialogPane()
            .lookupButton(submitType);
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            if (
                validateField(
                    input,
                    error,
                    "Vui lòng nhập một số nguyên"
                ).isEmpty()
            ) {
                event.consume();
            }
        });
        input.setOnAction(event -> submit.fire());
        dialog.setResultConverter(button ->
            button == submitType ? Integer.parseInt(input.getText()) : null
        );
        dialog.setOnShown(event -> Platform.runLater(input::requestFocus));
        dialog.showAndWait().ifPresent(action);
    }

    private <T> Dialog<T> createDialog(String title) {
        Dialog<T> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("operation-dialog");
        pane.setPrefWidth(390);
        pane.setMinWidth(360);
        if (view.getScene() != null) {
            dialog.initOwner(view.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            pane.getStylesheets().addAll(view.getScene().getStylesheets());
        }
        return dialog;
    }

    private ButtonType addDialogButtons(
        Dialog<?> dialog,
        String actionText,
        boolean danger
    ) {
        ButtonType cancel = new ButtonType(
            "Hủy",
            ButtonBar.ButtonData.CANCEL_CLOSE
        );
        ButtonType submit = new ButtonType(
            actionText,
            ButtonBar.ButtonData.OK_DONE
        );
        dialog.getDialogPane().getButtonTypes().setAll(cancel, submit);
        Node submitButton = dialog.getDialogPane().lookupButton(submit);
        submitButton
            .getStyleClass()
            .add(danger ? "dialog-danger-button" : "primary-button");
        return submit;
    }

    private void runOperation(OperationResult result) {
        lastComparisons = 0;
        animationManager.load(result.events());
        populateSteps(result.events());
        showToast(result.message(), !result.success());
        if (result.events().isEmpty()) {
            refreshTree();
        } else {
            animationManager.play();
        }
    }


    private void populateSteps(List<TreeAnimationEvent> events) {
        stepsBox.getChildren().clear();
        for (int i = 0; i < events.size(); i++) {
            stepsBox
                .getChildren()
                .add(createStepRow(events.get(i), i, events.size()));
        }
        refreshStepHighlights();
        boolean empty = events.isEmpty();
        stepsScroll.setVisible(!empty);
        stepsScroll.setManaged(!empty);
        stepsPlaceholder.setVisible(empty);
        stepsPlaceholder.setManaged(empty);
    }

    private HBox createStepRow(TreeAnimationEvent item, int index, int total) {
        int current = animationManager.getCurrentIndex();
        String state =
            index < current
                ? "completed"
                : index == current
                  ? "active"
                  : "pending";
        Label indicator = new Label(stepIndicatorText(item, state));
        indicator
            .getStyleClass()
            .addAll("step-indicator", "step-indicator-" + state);
        Label title = new Label(item.title());
        title.setWrapText(true);
        title.getStyleClass().add("step-title");
        Label detail = new Label(item.detail());
        detail.setWrapText(true);
        detail.setTextOverrun(OverrunStyle.CLIP);
        detail.setMinHeight(Region.USE_PREF_SIZE);
        detail.getStyleClass().add("step-detail");
        VBox copy = new VBox(2, title, detail);
        copy.setFillWidth(true);
        HBox row = new HBox(10, indicator, copy);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().addAll("step-row", "step-" + state);
        row.setOnMouseClicked(e -> animationManager.jumpTo(index));
        row.setCursor(javafx.scene.Cursor.HAND);
        HBox.setHgrow(copy, Priority.ALWAYS);
        return row;
    }

    private String stepIndicatorText(TreeAnimationEvent event, String state) {
        if (event.type() == EventType.ERROR) {
            return "×";
        }
        return switch (state) {
            case "completed" -> "✓";
            case "active" -> "●";
            default -> "○";
        };
    }

    private void rebalanceTree() {
        if (service.getTree().isEmpty()) {
            showToast("Không thể cân bằng vì cây đang trống", true);
            return;
        }
        OperationResult result = service.rebalance();
        if (result.success() && !result.values().isEmpty()) {
            insertionOrder = new ArrayList<>(result.values());
        }
        runOperation(result);
    }

    private void resetTree() {
        service.clear();
        lastComparisons = 0;
        insertionOrder = new ArrayList<>();
        clearTimelineAndRenderLive();
        showToast("Đã đặt lại cây và trạng thái mô phỏng", false);
    }

    private MenuButton buildSamplesMenu() {
        MenuItem randomCount = new MenuItem("Thêm ngẫu nhiên theo số lượng...");
        randomCount.setOnAction(event -> showRandomDialog(false));
        MenuItem randomAdvanced = new MenuItem(
            "Thêm ngẫu nhiên theo khoảng..."
        );
        randomAdvanced.setOnAction(event -> showRandomDialog(true));

        MenuButton menu = new MenuButton("Dữ liệu mẫu", icon(ICON_SAMPLE));
        menu.getItems().addAll(
            randomCount,
            randomAdvanced,
            presetItem(
                "Mẫu nhỏ · tổng quan nhanh",
                "Dữ liệu mẫu nhỏ",
                List.of(8, 18, 5, 15, 25, 2, 12, 20)
            ),
            presetItem(
                "Tách nút · nhiều tầng",
                "Minh họa tách nút",
                List.of(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 35, 45)
            ),
            presetItem(
                "Gộp nút · xóa khóa 30",
                "Minh họa gộp nút",
                List.of(10, 20, 30, 40, 50, 60)
            ),
            presetItem(
                "01 · Tăng dần",
                "Mẫu tăng dần",
                List.of(
                    5,
                    10,
                    15,
                    20,
                    25,
                    30,
                    35,
                    40,
                    45,
                    50,
                    55,
                    60,
                    65,
                    70,
                    75
                )
            ),
            presetItem(
                "02 · Giảm dần",
                "Mẫu giảm dần",
                List.of(100, 90, 80, 70, 60, 50, 40, 30, 20, 10)
            ),
            presetItem(
                "03 · Toàn số âm",
                "Mẫu số âm",
                List.of(-90, -70, -55, -40, -25, -10, -5, -2)
            ),
            presetItem(
                "04 · Âm và dương",
                "Mẫu âm dương",
                List.of(-50, 20, -10, 40, 0, 15, -25, 60, 5, -5)
            ),
            presetItem(
                "05 · Dữ liệu dày",
                "Mẫu dữ liệu dày",
                List.of(21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34)
            ),
            presetItem(
                "06 · Dữ liệu thưa",
                "Mẫu dữ liệu thưa",
                List.of(1, 100, 250, 500, 1_000, 2_500, 5_000, 10_000)
            ),
            presetItem(
                "07 · Khóa nhiều chữ số",
                "Mẫu khóa nhiều chữ số",
                List.of(
                    12_345,
                    54_321,
                    100_000,
                    250_000,
                    999_999,
                    -123_456,
                    1_234_567
                )
            ),
            presetItem(
                "08 · Chẵn và lẻ xen kẽ",
                "Mẫu chẵn lẻ",
                List.of(2, 11, 4, 13, 6, 15, 8, 17, 10, 19, 12, 21)
            ),
            presetItem(
                "09 · Cây sâu",
                "Mẫu cây sâu",
                List.of(
                    44,
                    12,
                    76,
                    5,
                    19,
                    52,
                    88,
                    2,
                    8,
                    15,
                    24,
                    48,
                    61,
                    81,
                    95,
                    1,
                    3,
                    7,
                    10,
                    14,
                    17,
                    22,
                    27,
                    46,
                    50,
                    57,
                    65,
                    79,
                    84,
                    92,
                    99
                )
            )
        );
        menu.getStyleClass().add("secondary-button");
        return menu;
    }

    private MenuItem presetItem(
        String label,
        String operation,
        List<Integer> values
    ) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(event -> loadPreset(operation, values));
        return item;
    }

    private void showRandomDialog(boolean advanced) {
        String title = advanced
            ? "Thêm khóa ngẫu nhiên nâng cao"
            : "Thêm khóa ngẫu nhiên";
        Dialog<RandomRequest> dialog = createDialog(title);
        TextField countInput = integerField("Ví dụ: 10");
        countInput.setText("10");
        TextField minInput = integerField("Nhỏ nhất");
        minInput.setText(advanced ? "-100" : "0");
        TextField maxInput = integerField("Lớn nhất");
        maxInput.setText(advanced ? "100" : "999");
        Label error = validationLabel();

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(8);
        fields.add(controlLabel("Số lượng khóa"), 0, 0, 2, 1);
        fields.add(countInput, 0, 1, 2, 1);
        if (advanced) {
            fields.add(controlLabel("Giá trị nhỏ nhất"), 0, 2);
            fields.add(controlLabel("Giá trị lớn nhất"), 1, 2);
            fields.add(minInput, 0, 3);
            fields.add(maxInput, 1, 3);
            fields.add(error, 0, 4, 2, 1);
        } else {
            Label hint = new Label(
                "Các khóa được tạo trong khoảng từ 0 đến 999."
            );
            hint.getStyleClass().add("section-caption");
            fields.add(hint, 0, 2, 2, 1);
            fields.add(error, 0, 3, 2, 1);
        }
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(50);
        fields.getColumnConstraints().addAll(column, column);
        dialog.getDialogPane().setContent(fields);

        ButtonType submitType = addDialogButtons(
            dialog,
            "Thêm ngẫu nhiên",
            false
        );
        Button submit = (Button) dialog
            .getDialogPane()
            .lookupButton(submitType);
        submit.addEventFilter(ActionEvent.ACTION, event -> {
            Optional<Integer> count = validateField(
                countInput,
                error,
                "Nhập số lượng khóa"
            );
            Optional<Integer> minimum = validateField(
                minInput,
                error,
                "Nhập giá trị nhỏ nhất"
            );
            Optional<Integer> maximum = validateField(
                maxInput,
                error,
                "Nhập giá trị lớn nhất"
            );
            if (count.isEmpty() || minimum.isEmpty() || maximum.isEmpty()) {
                event.consume();
                return;
            }
            if (count.get() < 1 || count.get() > 100) {
                error.setText("Số lượng khóa phải từ 1 đến 100");
                countInput.requestFocus();
                event.consume();
                return;
            }
            if (minimum.get() > maximum.get()) {
                error.setText(
                    "Giá trị nhỏ nhất không được lớn hơn giá trị lớn nhất"
                );
                minInput.requestFocus();
                event.consume();
                return;
            }
            long occupied = service
                .getTree()
                .keys()
                .stream()
                .filter(key -> key >= minimum.get() && key <= maximum.get())
                .count();
            long capacity =
                (long) maximum.get() - minimum.get() + 1L - occupied;
            if (capacity < count.get()) {
                error.setText("Khoảng đã chọn không còn đủ khóa duy nhất");
                event.consume();
            }
        });
        dialog.setResultConverter(button ->
            button == submitType
                ? new RandomRequest(
                      Integer.parseInt(countInput.getText()),
                      Integer.parseInt(minInput.getText()),
                      Integer.parseInt(maxInput.getText())
                  )
                : null
        );
        dialog.setOnShown(event -> Platform.runLater(countInput::requestFocus));
        countInput.setOnAction(event -> submit.fire());
        dialog.showAndWait().ifPresent(this::addRandomKeys);
    }

    private void addRandomKeys(RandomRequest request) {
        Random random = new Random();
        Set<Integer> generated = new LinkedHashSet<>();
        long upperExclusive = (long) request.maximum() + 1L;
        while (generated.size() < request.count()) {
            int candidate = (int) random.nextLong(
                request.minimum(),
                upperExclusive
            );
            if (!service.getTree().contains(candidate)) {
                generated.add(candidate);
            }
        }
        generated.forEach(value -> service.getTree().insert(value));
        service.getTree().assertValid();
        insertionOrder.addAll(generated);
        List<Integer> added = List.copyOf(generated);
        lastComparisons = 0;
        clearTimelineAndRenderLive();
        showToast("Đã thêm " + added.size() + " khóa ngẫu nhiên", false);
    }

    private void loadPreset(String name, List<Integer> values) {
        service.clear();
        values.forEach(value -> service.getTree().insert(value));
        service.getTree().assertValid();
        insertionOrder = new ArrayList<>(values);
        lastComparisons = 0;
        clearTimelineAndRenderLive();
        showToast(
            "Đã nạp " + name.toLowerCase() + " với " + values.size() + " khóa",
            false
        );
    }

    private void refreshTree() {
        renderer.render(service.getTree());
        updateInformation();
        updateEmptyState();
    }

    private void clearTimelineAndRenderLive() {
        animationManager.load(List.of());
        stepsBox.getChildren().clear();
        stepsScroll.setVisible(false);
        stepsScroll.setManaged(false);
        stepsPlaceholder.setVisible(true);
        stepsPlaceholder.setManaged(true);
        renderer.clearHighlights();
        refreshTree();
    }

    private void showAnimationEvent(TreeAnimationEvent event) {
        renderer.render(event);
        lastComparisons = event.comparisons();
        updateInformation(event.snapshot());
        updateEmptyState(event.snapshot().isEmpty());
    }

    private void updateInformation() {
        BPlusTreeStatistics statistics = service.getTree().statistics();
        updateInformation(
            statistics.height(),
            statistics.keyCount(),
            statistics.nodeCount(),
            statistics.internalNodeCount(),
            statistics.leafNodeCount()
        );
    }

    private void updateInformation(TreeSnapshot snapshot) {
        int leafNodes = (int) snapshot
            .nodes()
            .stream()
            .filter(NodeSnapshot::leaf)
            .count();
        updateInformation(
            snapshotHeight(snapshot),
            snapshot.size(),
            snapshot.nodes().size(),
            snapshot.nodes().size() - leafNodes,
            leafNodes
        );
    }

    private void updateInformation(
        int height,
        int keyCount,
        int nodeCount,
        int internalNodeCount,
        int leafNodeCount
    ) {
        statisticValues.get("height").setText(Integer.toString(height));
        statisticValues.get("keys").setText(Integer.toString(keyCount));
        statisticValues.get("nodes").setText(Integer.toString(nodeCount));
        statisticValues
            .get("internal")
            .setText(Integer.toString(internalNodeCount));
        statisticValues.get("leaf").setText(Integer.toString(leafNodeCount));
        statisticValues
            .get("comparisons")
            .setText(Integer.toString(lastComparisons));
    }

    private int snapshotHeight(TreeSnapshot snapshot) {
        int height = 0;
        Optional<NodeSnapshot> current = snapshot.root();
        Set<Long> visited = new LinkedHashSet<>();
        while (current.isPresent() && visited.add(current.get().id())) {
            height++;
            List<Long> children = current.get().childIds();
            current = children.isEmpty()
                ? Optional.empty()
                : snapshot.node(children.get(0));
        }
        return height;
    }

    private void updateEmptyState() {
        updateEmptyState(service.getTree().isEmpty());
    }

    private void updateEmptyState(boolean empty) {
        emptyContent.setVisible(empty);
        emptyContent.setManaged(empty);
        renderer.setVisible(!empty);
    }

    private void showToast(String message, boolean error) {
        if (toastAnimation != null) {
            toastAnimation.stop();
        }
        toast.setText((error ? "" : "✓  ") + message);
        toast.getStyleClass().removeAll("toast-success", "toast-error");
        toast.getStyleClass().add(error ? "toast-error" : "toast-success");
        toast.setOpacity(0);
        toast.setVisible(true);
        toast.setManaged(true);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(140), toast);
        fadeIn.setToValue(1);
        PauseTransition wait = new PauseTransition(Duration.seconds(2.6));
        FadeTransition fadeOut = new FadeTransition(
            Duration.millis(220),
            toast
        );
        fadeOut.setToValue(0);
        toastAnimation = new SequentialTransition(fadeIn, wait, fadeOut);
        toastAnimation.setOnFinished(event -> {
            toast.setVisible(false);
            toast.setManaged(false);
        });
        toastAnimation.play();
    }

    private void addStatistic(
        GridPane grid,
        int column,
        int row,
        String labelText,
        String key
    ) {
        Label name = new Label(labelText);
        name.getStyleClass().add("stat-name");
        Label value = new Label("—");
        value.getStyleClass().add("stat-value");
        VBox card = new VBox(4, name, value);
        card.getStyleClass().add("stat-card");
        card.setMaxWidth(Double.MAX_VALUE);
        statisticValues.put(key, value);
        grid.add(card, column, row);
    }

    private static TextField integerField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setTextFormatter(
            new TextFormatter<String>(change ->
                change.getControlNewText().matches("-?\\d*") ? change : null
            )
        );
        return field;
    }

    private static Optional<Integer> validateField(
        TextField field,
        Label error,
        String emptyMessage
    ) {
        String text = field.getText().trim();
        if (text.isEmpty() || "-".equals(text)) {
            error.setText(emptyMessage);
            field.requestFocus();
            return Optional.empty();
        }
        try {
            error.setText("");
            return Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException exception) {
            error.setText("Giá trị nằm ngoài phạm vi số nguyên hợp lệ");
            field.requestFocus();
            field.selectAll();
            return Optional.empty();
        }
    }

    private static Label validationLabel() {
        Label error = new Label();
        error.getStyleClass().add("dialog-error");
        error.setMinHeight(18);
        return error;
    }

    private static Label controlLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("control-label");
        return label;
    }

    private static Button actionButton(
        String text,
        String iconPath,
        String styleClass,
        Runnable action
    ) {
        Button button = new Button(text, icon(iconPath));
        button.getStyleClass().add(styleClass);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Button iconButton(
        String iconPath,
        String accessibleText,
        Runnable action
    ) {
        Button button = new Button(null, icon(iconPath));
        button.getStyleClass().addAll("animation-secondary", "icon-button");
        button.setAccessibleText(accessibleText);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static SVGPath icon(String content) {
        SVGPath icon = new SVGPath();
        icon.setContent(content);
        icon.getStyleClass().add("button-icon");
        return icon;
    }

    private record RandomRequest(int count, int minimum, int maximum) {}
}
