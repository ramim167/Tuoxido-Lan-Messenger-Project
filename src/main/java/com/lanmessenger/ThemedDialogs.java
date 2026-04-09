package com.lanmessenger;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class ThemedDialogs {
    private static final String CARD_STYLE =
            "-fx-background-color: -lm-panel;" +
            "-fx-background-radius: 24;" +
            "-fx-border-color: -lm-border;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 24;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 28, 0.18, 0, 10);";

    private static final String SECONDARY_BUTTON_STYLE =
            "-fx-background-color: rgba(255,255,255,0.08);" +
            "-fx-text-fill: -lm-text;" +
            "-fx-background-radius: 14;" +
            "-fx-border-color: -lm-border;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 14;" +
            "-fx-font-weight: 800;" +
            "-fx-padding: 10 16;" +
            "-fx-cursor: hand;";

    private static final String PRIMARY_BUTTON_STYLE =
            "-fx-background-color: #2563eb;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 14;" +
            "-fx-font-weight: 900;" +
            "-fx-padding: 10 18;" +
            "-fx-cursor: hand;";

    private static final String DANGER_BUTTON_STYLE =
            "-fx-background-color: #ef4444;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 14;" +
            "-fx-font-weight: 900;" +
            "-fx-padding: 10 18;" +
            "-fx-cursor: hand;";

    private ThemedDialogs() {}

    public static void showAlert(Window owner, String titleText, String messageText, boolean warning) {
        Stage stage = createStage(owner);
        VBox card = buildCard(warning ? "Warning" : "Notice", titleText, messageText);

        Button okButton = createActionButton("OK", warning);
        okButton.setOnAction(e -> stage.close());

        HBox actions = new HBox(okButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().add(actions);

        stage.setScene(buildScene(card));
        stage.show();
    }

    public static void showConfirmation(
            Window owner,
            String badgeText,
            String titleText,
            String messageText,
            String cancelText,
            String confirmText,
            boolean dangerConfirm,
            Runnable onConfirm
    ) {
        Stage stage = createStage(owner);
        VBox card = buildCard(badgeText, titleText, messageText);

        Button cancelButton = createSecondaryButton(cancelText);
        cancelButton.setOnAction(e -> stage.close());

        Button confirmButton = createActionButton(confirmText, dangerConfirm);
        confirmButton.setTextAlignment(TextAlignment.CENTER);
        confirmButton.setWrapText(true);
        confirmButton.setOnAction(e -> {
            stage.close();
            if (onConfirm != null) {
                onConfirm.run();
            }
        });

        HBox actions = new HBox(12, cancelButton, confirmButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().add(actions);

        stage.setScene(buildScene(card));
        stage.show();
    }

    private static Stage createStage(Window owner) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setResizable(false);
        return stage;
    }

    private static Scene buildScene(VBox card) {
        StackPane overlay = new StackPane(card);
        overlay.setPadding(new Insets(26));
        overlay.setStyle("-fx-background-color: rgba(15, 23, 42, 0.24);");

        Scene scene = new Scene(overlay);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyMainTheme(scene);
        return scene;
    }

    private static VBox buildCard(String badgeText, String titleText, String messageText) {
        Label badge = new Label(badgeText);
        badge.setStyle(
                "-fx-background-color: rgba(37,99,235,0.12);" +
                "-fx-text-fill: #2563eb;" +
                "-fx-background-radius: 999;" +
                "-fx-padding: 5 10;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: 900;"
        );

        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: -lm-text;");

        Label message = new Label(messageText);
        message.setWrapText(true);
        message.setTextAlignment(TextAlignment.LEFT);
        message.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: -lm-muted; -fx-line-spacing: 2px;");

        VBox copy = new VBox(10, badge, title, message);
        copy.setFillWidth(true);
        HBox.setHgrow(copy, Priority.ALWAYS);

        VBox card = new VBox(18);
        card.setMaxWidth(380);
        card.setPadding(new Insets(24));
        card.setStyle(CARD_STYLE);
        card.getChildren().add(copy);
        return card;
    }

    private static Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(SECONDARY_BUTTON_STYLE);
        return button;
    }

    private static Button createActionButton(String text, boolean danger) {
        Button button = new Button(text);
        button.setStyle(danger ? DANGER_BUTTON_STYLE : PRIMARY_BUTTON_STYLE);
        return button;
    }
}
