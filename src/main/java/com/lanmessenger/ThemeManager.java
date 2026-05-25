package com.lanmessenger;

import javafx.application.Platform;
import javafx.scene.Scene;

public final class ThemeManager {
    private ThemeManager() {}

    public static void applyMainTheme(Scene scene) {
        if (scene == null) return;

        scene.getStylesheets().clear();

        String css = (MainApp.currentTheme == MainApp.Theme.DARK) ? "/main_dark.css" : "/main.css";
        var res = MainApp.class.getResource(css);
        if (res != null) {
            scene.getStylesheets().add(res.toExternalForm());
        }

        // Ensure smooth scrolling behavior is consistently applied after theme/root updates.
        Platform.runLater(() -> SmoothScrollUtil.applyToScene(scene));
    }
}
