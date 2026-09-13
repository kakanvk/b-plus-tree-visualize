package com.bplustree.visualizer.ui;

import atlantafx.base.theme.PrimerLight;
import com.bplustree.visualizer.controller.MainController;
import java.util.Objects;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** JavaFX application entry point. */
public final class MainApplication extends Application {
    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        MainController controller = new MainController();
        Screen screen = Screen.getPrimary();
        Rectangle2D visualBounds = screen.getVisualBounds();
        double availableWidth = visualBounds.getWidth() / screen.getOutputScaleX();
        double availableHeight = visualBounds.getHeight() / screen.getOutputScaleY();
        double initialWidth = Math.min(1280, availableWidth - 24);
        double initialHeight = Math.min(800, availableHeight - 48);
        Scene scene = new Scene(controller.getView(), initialWidth, initialHeight);
        scene.getStylesheets().add(Objects.requireNonNull(
                MainApplication.class.getResource("/styles/app.css"),
                "Missing application stylesheet").toExternalForm());
        stage.setTitle("Trực quan hóa cây B+");
        stage.setScene(scene);
        stage.setMinWidth(Math.min(1024, availableWidth));
        stage.setMinHeight(Math.min(700, availableHeight));
        stage.setMaximized(true);
        stage.show();
        if (screen.getOutputScaleX() > 1.0 || screen.getOutputScaleY() > 1.0) {
            stage.setX(visualBounds.getMinX());
            stage.setY(visualBounds.getMinY());
        } else {
            stage.centerOnScreen();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
