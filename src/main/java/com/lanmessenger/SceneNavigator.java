package com.lanmessenger;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.util.Duration;

public final class SceneNavigator {
    private static final Duration FADE_OUT = Duration.millis(280);
    private static final Duration FADE_IN = Duration.millis(360);
    private static final Duration LIFT_IN = Duration.millis(380);
    private static final Duration SCALE_IN = Duration.millis(380);

    private SceneNavigator() {}

    public static void swapRootWithFade(Scene scene, String fxmlPath, String cssPath) throws Exception {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(fxmlPath));
        Parent nextRoot = loader.load();
        nextRoot.setOpacity(0);

        FadeTransition out = new FadeTransition(FADE_OUT, scene.getRoot());
        out.setFromValue(1);
        out.setToValue(0);
        out.setInterpolator(Interpolator.EASE_BOTH);

        out.setOnFinished(ev -> {
            scene.setRoot(nextRoot);
            scene.getStylesheets().clear();

            var css = MainApp.class.getResource(cssPath);
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }

            Platform.runLater(() -> SmoothScrollUtil.applyToScene(scene));
            playEntrance(nextRoot);
        });

        out.play();
    }

    public static void swapRootWithFade(ActionEvent event, Node fallbackNode, String fxmlPath, String cssPath) throws Exception {
        Scene scene = resolveScene(event, fallbackNode);
        if (scene == null) {
            throw new IllegalStateException("No active scene found for navigation.");
        }
        swapRootWithFade(scene, fxmlPath, cssPath);
    }

    public static void swapRootWithMainTheme(ActionEvent event, Node fallbackNode, String fxmlPath) throws Exception {
        String cssPath = MainApp.currentTheme == MainApp.Theme.DARK ? "/main_dark.css" : "/main.css";
        swapRootWithFade(event, fallbackNode, fxmlPath, cssPath);
    }

    // Prefer the source control's scene and fall back to the known root node.
    private static Scene resolveScene(ActionEvent event, Node fallbackNode) {
        if (event != null && event.getSource() instanceof Node sourceNode && sourceNode.getScene() != null) {
            return sourceNode.getScene();
        }
        return fallbackNode == null ? null : fallbackNode.getScene();
    }

    public static void playEntrance(Node node) {
        if (node == null) return;

        node.setOpacity(0);
        node.setTranslateY(18);

        FadeTransition fade = new FadeTransition(FADE_IN, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale = new ScaleTransition(SCALE_IN, node);
        scale.setFromX(0.988);
        scale.setFromY(0.988);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition lift = new TranslateTransition(LIFT_IN, node);
        lift.setFromY(18);
        lift.setToY(0);
        lift.setInterpolator(Interpolator.EASE_BOTH);

        new ParallelTransition(fade, scale, lift).play();
    }
    public static void swapRootInstant(Scene scene, String fxmlPath, String cssPath) throws Exception {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(fxmlPath));
        Parent nextRoot = loader.load();

        scene.setRoot(nextRoot);
        scene.getStylesheets().clear();

        var css = MainApp.class.getResource(cssPath);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        Platform.runLater(() -> SmoothScrollUtil.applyToScene(scene));
    }

    public static void swapRootInstant(ActionEvent event, Node fallbackNode, String fxmlPath, String cssPath) throws Exception {
        Scene scene = resolveScene(event, fallbackNode);
        if (scene == null) {
            throw new IllegalStateException("No active scene found for navigation.");
        }
        swapRootInstant(scene, fxmlPath, cssPath);
    }

    public static void swapRootWithMainThemeInstant(ActionEvent event, Node fallbackNode, String fxmlPath) throws Exception {
        String cssPath = MainApp.currentTheme == MainApp.Theme.DARK ? "/main_dark.css" : "/main.css";
        swapRootInstant(event, fallbackNode, fxmlPath, cssPath);
    }
}
