package com.lanmessenger;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.bson.Document;

import java.util.List;

public class BlockedUsersController {

    @FXML private VBox blockedUsersList;

    private volatile boolean active = true;

    @FXML
    public void initialize() {
        blockedUsersList.sceneProperty().addListener((obs, oldScene, newScene) -> active = newScene != null);
        loadBlockedUsers();
    }

    private void loadBlockedUsers() {
        if (!isUiAvailable()) return;

        blockedUsersList.getChildren().clear();
        Label loading = new Label("Loading blocked users...");
        loading.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 13px;");
        blockedUsersList.getChildren().add(loading);

        UserProfile me = Session.getProfile();
        if (me == null) return;

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return UserService.getBlockedUsersList(me.username);
            }
        };

        task.setOnSucceeded(e -> {
            if (!isUiAvailable()) return;

            blockedUsersList.getChildren().clear();
            List<String> blockedUsers = task.getValue();

            if (blockedUsers == null || blockedUsers.isEmpty()) {
                Label empty = new Label("You haven't blocked anyone.");
                empty.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 13px;");
                blockedUsersList.getChildren().add(empty);
                return;
            }

            for (String blockedUsername : blockedUsers) {
                Document doc = UserService.getUserByUsername(blockedUsername);
                String name = doc != null ? doc.getString("name") : blockedUsername;
                String profilePicUrl = doc != null ? doc.getString("profilePic") : null;

                HBox card = new HBox(15);
                card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                card.setStyle("-fx-background-color: transparent; -fx-padding: 10; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0;");

                ImageView imgView = new ImageView();
                imgView.setFitWidth(45);
                imgView.setFitHeight(45);
                imgView.setClip(new javafx.scene.shape.Circle(22.5, 22.5, 22.5));
                if (profilePicUrl != null && profilePicUrl.startsWith("http")) {
                    imgView.setImage(new Image(profilePicUrl, true));
                } else {
                    try {
                        imgView.setImage(new Image(getClass().getResourceAsStream("/assets/default_avatar.png")));
                    } catch (Exception ignored) {
                    }
                }

                VBox nameBox = new VBox(2);
                Label nameLabel = new Label(name);
                nameLabel.setStyle("-fx-text-fill: -lm-text; -fx-font-size: 14.5px; -fx-font-weight: 800;");
                Label usernameLabel = new Label("@" + blockedUsername);
                usernameLabel.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 12px;");
                nameBox.getChildren().addAll(nameLabel, usernameLabel);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button unblockBtn = new Button("Unblock");
                unblockBtn.setStyle("-fx-background-color: -lm-soft; -fx-text-fill: -lm-text; -fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-weight: bold;");
                unblockBtn.setOnAction(ev -> {
                    unblockBtn.setText("Wait...");
                    unblockBtn.setDisable(true);

                    Thread unblockThread = new Thread(() -> {
                        boolean success = UserService.unblockUser(me.username, blockedUsername);
                        Platform.runLater(() -> {
                            if (!isUiAvailable()) return;

                            if (success) {
                                loadBlockedUsers();
                            } else {
                                unblockBtn.setText("Error");
                                unblockBtn.setDisable(false);
                            }
                        });
                    }, "unblock-user");

                    unblockThread.setDaemon(true);
                    unblockThread.start();
                });

                card.getChildren().addAll(imgView, nameBox, spacer, unblockBtn);
                card.setOnMouseEntered(ev -> card.setStyle("-fx-background-color: -lm-hover; -fx-padding: 10; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0; -fx-background-radius: 8;"));
                card.setOnMouseExited(ev -> card.setStyle("-fx-background-color: transparent; -fx-padding: 10; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0;"));

                blockedUsersList.getChildren().add(card);
            }
        });

        Thread loadThread = new Thread(task, "blocked-users-load");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    @FXML
    private void onBackToMain(ActionEvent event) {
        try {
            active = false;
            Stage stage = (Stage) blockedUsersList.getScene().getWindow();
            Scene scene = stage.getScene();
            Parent root = new FXMLLoader(MainApp.class.getResource("/main.fxml")).load();
            root.setOpacity(0);
            FadeTransition out = new FadeTransition(Duration.millis(200), scene.getRoot());
            out.setFromValue(1);
            out.setToValue(0);
            out.setOnFinished(ev -> {
                scene.setRoot(root);
                ThemeManager.applyMainTheme(scene);
                FadeTransition in = new FadeTransition(Duration.millis(250), root);
                in.setFromValue(0);
                in.setToValue(1);
                in.play();
            });
            out.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isUiAvailable() {
        return active && blockedUsersList != null;
    }
}
