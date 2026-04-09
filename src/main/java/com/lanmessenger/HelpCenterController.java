package com.lanmessenger;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.CacheHint;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

public class HelpCenterController {
    @FXML
    private ScrollPane root;

    @FXML
    public void initialize() {
        SmoothScrollUtil.applyLight(root);
        optimizeScrollContent();
    }

    private void optimizeScrollContent() {
        if (root == null) return;
        root.setPannable(false);
        javafx.application.Platform.runLater(() -> {
            if (root.getContent() != null) {
                root.getContent().setCache(true);
                root.getContent().setCacheHint(CacheHint.SPEED);
            }
        });
    }

    @FXML
    private void onOpenFeatures(ActionEvent e) {
        openPage("/features.fxml");
    }

    @FXML
    private void onOpenPrivacySafety(ActionEvent e) {
        openPage("/privacy_safety.fxml");
    }

    @FXML
    private void onOpenDevelopers(ActionEvent e) {
        openPage("/developers.fxml");
    }

    @FXML
    private void onOpenHelpCenter(ActionEvent e) {}

    @FXML
    private void onBackToLogin(ActionEvent e) {
        try {
            SceneNavigator.swapRootWithFade(e, root, "/login.fxml", "/login.css");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void openPage(String fxmlPath) {
        openPage(fxmlPath, "/info_pages.css");
    }

    private void openPage(String fxmlPath, String cssPath) {
        try {
            Stage stage = (Stage) root.getScene().getWindow();
            SceneNavigator.swapRootWithFade(stage.getScene(), fxmlPath, cssPath);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
