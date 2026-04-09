package com.lanmessenger;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.function.Consumer;

public final class MomentEditorDialogs {
    private static final int MAX_TEXT = 300;

    private MomentEditorDialogs() {}

    public static void showCaptionEditor(Window owner, String initialCaption, Consumer<String> onSave) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);
        if (owner != null) {
            stage.initOwner(owner);
        }

        Label badge = new Label("Moment");
        badge.setStyle(
                "-fx-background-color: rgba(37,99,235,0.12);" +
                "-fx-text-fill: #2563eb;" +
                "-fx-background-radius: 999;" +
                "-fx-padding: 5 10;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: 900;"
        );

        Label title = new Label("Edit Caption");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: -lm-text;");

        Label subtitle = new Label("Update the text shown on this moment.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: -lm-muted;");

        TextArea captionArea = new TextArea(initialCaption == null ? "" : initialCaption);
        captionArea.setWrapText(true);
        captionArea.setPrefRowCount(4);
        captionArea.getStyleClass().add("moments-input");

        Label counter = new Label();
        counter.getStyleClass().add("moment-meta");
        captionArea.textProperty().addListener((obs, oldValue, newValue) -> {
            String safe = newValue == null ? "" : newValue;
            if (safe.length() > MAX_TEXT) {
                captionArea.setText(safe.substring(0, MAX_TEXT));
                return;
            }
            counter.setText(safe.length() + "/" + MAX_TEXT);
        });
        counter.setText(captionArea.getText().length() + "/" + MAX_TEXT);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("secondary-btn");
        cancelButton.setOnAction(e -> stage.close());

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("send-btn");
        saveButton.setOnAction(e -> {
            stage.close();
            if (onSave != null) {
                onSave.accept(captionArea.getText() == null ? "" : captionArea.getText().trim());
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(12, counter, spacer, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14, badge, title, subtitle, captionArea, actions);
        card.setPadding(new Insets(24));
        card.setMaxWidth(420);
        card.setStyle(
                "-fx-background-color: -lm-panel;" +
                "-fx-background-radius: 24;" +
                "-fx-border-color: -lm-border;" +
                "-fx-border-width: 1;" +
                "-fx-border-radius: 24;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 28, 0.18, 0, 10);"
        );

        StackPane overlay = new StackPane(card);
        overlay.setPadding(new Insets(26));
        overlay.setStyle("-fx-background-color: rgba(15,23,42,0.28);");

        Scene scene = new Scene(overlay);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyMainTheme(scene);

        stage.setScene(scene);
        stage.show();
        Platform.runLater(captionArea::requestFocus);
    }
}
