package com.lanmessenger;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class FriendsController {

    @FXML private TextField searchUserField;
    @FXML private Button searchBtn;
    @FXML private VBox searchResultsBox;
    @FXML private VBox requestListBox;
    @FXML private VBox myFriendsBox;

    @FXML private ScrollPane friendsScroll;
    @FXML private ScrollPane resultsScroll;
    @FXML private ScrollPane requestsScroll;

    private PauseTransition searchDebounce = new PauseTransition(Duration.millis(500));
    private volatile boolean keepListening = false;
    private volatile boolean active = true;
    private Thread liveUpdateThread;
    private int lastReqCount = -1;
    private int lastFriendCount = -1;
    @FXML
    public void initialize() {
        searchUserField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                active = false;
                stopLiveUpdate();
            } else {
                active = true;
            }
        });
        SmoothScrollUtil.apply(friendsScroll);
        SmoothScrollUtil.apply(resultsScroll);
        SmoothScrollUtil.apply(requestsScroll);
        showDefaultSearchText();

        searchUserField.textProperty().addListener((obs, oldVal, newVal) -> {
            searchDebounce.setOnFinished(e -> performSearch(newVal.trim()));
            searchDebounce.playFromStart();
        });
        loadPendingRequests();
        loadMyFriends();
        startLiveUpdate();
    }

    private void showDefaultSearchText() {
        searchResultsBox.getChildren().clear();
        Label emptySearchLabel = new Label("Search by name or @username...");
        emptySearchLabel.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 12px; -fx-opacity: 0.58; -fx-padding: 4 2 4 2;");
        searchResultsBox.getChildren().add(emptySearchLabel);
    }

    @FXML
    private void onBackToMain(ActionEvent event) {
        try {
            active = false;
            stopLiveUpdate();
            Scene scene = searchUserField.getScene();
            if (scene == null) return;

            String cssPath = MainApp.currentTheme == MainApp.Theme.DARK ? "/main_dark.css" : "/main.css";
            SceneNavigator.swapRootWithFade(scene, "/main.fxml", cssPath);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void onSearchUser(ActionEvent event) {
        performSearch(searchUserField.getText().trim());
    }

    private void performSearch(String query) {
        if (!isUiAvailable()) {
            return;
        }
        if (query.isEmpty()) {
            showDefaultSearchText();
            return;
        }

        searchResultsBox.getChildren().clear();
        Label searchingLabel = new Label("Searching...");
        searchingLabel.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 12px; -fx-opacity: 0.72; -fx-font-weight: 700; -fx-padding: 4 2 4 2;");
        searchResultsBox.getChildren().add(searchingLabel);

        javafx.concurrent.Task<java.util.List<org.bson.Document>> searchTask = new javafx.concurrent.Task<>() {
            @Override protected java.util.List<org.bson.Document> call() {
                UserProfile me = Session.getProfile();
                if (me == null) return new java.util.ArrayList<>();

                return UserService.searchFriendsByQuery(query, me.username);
            }
        };

        searchTask.setOnSucceeded(e -> {
            if (!isUiAvailable()) {
                return;
            }
            searchResultsBox.getChildren().clear();
            java.util.List<org.bson.Document> users = searchTask.getValue();

            if (users.isEmpty()) {
                Label noResultLabel = new Label("No users found matching '" + query + "'");
                noResultLabel.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 12px; -fx-opacity: 0.64; -fx-padding: 4 2 4 2;");
                searchResultsBox.getChildren().add(noResultLabel);
                return;
            }

            for (org.bson.Document userDoc : users) {
                String dbName = userDoc.getString("name");
                String dbUsername = userDoc.getString("username");
                if (UserService.isBlocked(Session.getProfile().username, dbUsername)) continue;
                String profilePicUrl = userDoc.getString("profilePic");

                String friendshipStatus = String.valueOf(userDoc.get("friendshipStatus"));

                javafx.scene.layout.HBox card = new javafx.scene.layout.HBox();
                card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                card.setSpacing(15);
                card.setStyle("-fx-background-color: transparent; -fx-padding: 10 4 10 4; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0;");

                javafx.scene.image.ImageView imgView = new javafx.scene.image.ImageView();
                imgView.setFitWidth(45); imgView.setFitHeight(45);
                javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(22.5, 22.5, 22.5);
                imgView.setClip(clip);

                if (profilePicUrl != null && profilePicUrl.startsWith("http")) {
                    imgView.setImage(new javafx.scene.image.Image(profilePicUrl, true));
                } else {
                    try {
                        imgView.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream("/assets/default_avatar.png")));
                    } catch (Exception ex) {}
                }

                javafx.scene.layout.VBox nameBox = new javafx.scene.layout.VBox(2);
                Label nameLabel = new Label(dbName);
                nameLabel.setStyle("-fx-text-fill: -lm-text; -fx-font-size: 14.5px; -fx-font-weight: 800;");
                Label usernameLabel = new Label("@" + dbUsername);
                usernameLabel.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 12px;");
                nameBox.getChildren().addAll(nameLabel, usernameLabel);

                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                Button actionBtn = new Button();

                if ("accepted".equalsIgnoreCase(friendshipStatus)) {

                    actionBtn.setText("Friends");
                    actionBtn.setStyle("-fx-background-color: #E5E7EB; -fx-text-fill: #6B7280; -fx-background-radius: 10; -fx-padding: 6 12 6 12; -fx-font-weight: bold;");
                    actionBtn.setDisable(true);
                } else if ("pending".equalsIgnoreCase(friendshipStatus)) {

                    actionBtn.setText("Request Sent");
                    actionBtn.setStyle("-fx-background-color: #E5E7EB; -fx-text-fill: #6B7280; -fx-background-radius: 10; -fx-padding: 6 12 6 12; -fx-font-weight: bold;");
                    actionBtn.setDisable(true);
                } else {

                    actionBtn.setText("Add Friend");
                    actionBtn.setStyle("-fx-background-color: -lm-accent; -fx-text-fill: white; -fx-background-radius: 10; -fx-padding: 6 12 6 12; -fx-font-weight: bold; -fx-cursor: hand;");

                    actionBtn.setOnAction(ev -> {
                        actionBtn.setText("Sending...");
                        actionBtn.setDisable(true);
                        javafx.concurrent.Task<Boolean> reqTask = new javafx.concurrent.Task<>() {
                            @Override protected Boolean call() {
                                UserProfile me = Session.getProfile();
                                return UserService.sendFriendRequest(me.username, dbUsername);
                            }
                        };
                        reqTask.setOnSucceeded(e_req -> {
                            if (reqTask.getValue()) {

                                actionBtn.setText("Request Sent");
                                actionBtn.setStyle("-fx-background-color: #E5E7EB; -fx-text-fill: #6B7280; -fx-background-radius: 10; -fx-padding: 6 12 6 12; -fx-font-weight: bold;");
                            } else {
                                actionBtn.setText("Error");
                                actionBtn.setDisable(false);
                            }
                        });
                        Thread requestThread = new Thread(reqTask, "send-friend-request");
                        requestThread.setDaemon(true);
                        requestThread.start();
                    });
                }

                card.getChildren().addAll(imgView, nameBox, spacer, actionBtn);

                card.setOnMouseEntered(ev -> card.setStyle("-fx-background-color: -lm-hover; -fx-padding: 10 8 10 8; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0; -fx-background-radius: 10;"));
                card.setOnMouseExited(ev -> card.setStyle("-fx-background-color: transparent; -fx-padding: 10 4 10 4; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0;"));

                searchResultsBox.getChildren().add(card);
            }
        });
        Thread searchThread = new Thread(searchTask, "friend-search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private void loadMyFriends() {
        if (!isUiAvailable()) {
            return;
        }
        myFriendsBox.getChildren().clear();
        javafx.concurrent.Task<java.util.List<org.bson.Document>> task = new javafx.concurrent.Task<>() {
            @Override protected java.util.List<org.bson.Document> call() {
                return UserService.getMyFriendsList(Session.getProfile().username);
            }
        };

        task.setOnSucceeded(e -> {
            if (!isUiAvailable()) {
                return;
            }
            myFriendsBox.getChildren().clear();
            for (org.bson.Document friend : task.getValue()) {
                String fName = friend.getString("name");
                String fUser = friend.getString("username");
                String fPic = friend.getString("profilePic");

                javafx.scene.layout.HBox card = new javafx.scene.layout.HBox(12);
                card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                card.setStyle("-fx-padding: 10 4 10 4; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0;");

                javafx.scene.image.ImageView img = new javafx.scene.image.ImageView();
                img.setFitWidth(35); img.setFitHeight(35);
                img.setClip(new javafx.scene.shape.Circle(17.5, 17.5, 17.5));
                img.setCursor(javafx.scene.Cursor.HAND);
                img.setOnMouseClicked(ev -> viewUserProfile(fUser));

                javafx.scene.layout.VBox nameBox = new javafx.scene.layout.VBox(1);
                Label nL = new Label(fName); nL.setStyle("-fx-font-weight: bold; -fx-text-fill: -lm-text; -fx-cursor: hand;");
                nL.setOnMouseClicked(ev -> viewUserProfile(fUser));
                Label uL = new Label("@" + fUser); uL.setStyle("-fx-font-size: 10px; -fx-text-fill: -lm-muted;");
                nameBox.getChildren().addAll(nL, uL);

                javafx.scene.layout.Region s = new javafx.scene.layout.Region();
                javafx.scene.layout.HBox.setHgrow(s, javafx.scene.layout.Priority.ALWAYS);

                javafx.scene.control.MenuButton dotsBtn = new javafx.scene.control.MenuButton();
                dotsBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

                javafx.scene.control.MenuItem viewItem = new javafx.scene.control.MenuItem("View Profile");
                javafx.scene.control.MenuItem removeItem = new javafx.scene.control.MenuItem("Remove Friend");
                javafx.scene.control.MenuItem blockItem = new javafx.scene.control.MenuItem("Block User");

                viewItem.setOnAction(ev -> viewUserProfile(fUser));
                removeItem.setOnAction(ev -> {
                    if(UserService.removeFriend(Session.getProfile().username, fUser)) loadMyFriends();
                });
                blockItem.setOnAction(ev -> {
                    if(UserService.blockUser(Session.getProfile().username, fUser)) loadMyFriends();
                });

                dotsBtn.getItems().addAll(viewItem, removeItem, blockItem);

                card.getChildren().addAll(img, nameBox, s, dotsBtn);
                card.setOnMouseEntered(ev -> card.setStyle("-fx-background-color: -lm-hover; -fx-padding: 10 8 10 8; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0; -fx-background-radius: 10;"));
                card.setOnMouseExited(ev -> card.setStyle("-fx-background-color: transparent; -fx-padding: 10 4 10 4; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0;"));
                myFriendsBox.getChildren().add(card);
            }
        });
        Thread loadThread = new Thread(task, "friends-list-load");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void loadPendingRequests() {
        if (!isUiAvailable()) {
            return;
        }
        requestListBox.getChildren().clear();
        Label loading = new Label("Loading requests...");
        loading.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 12px; -fx-opacity: 0.64; -fx-padding: 4 2 4 2;");
        requestListBox.getChildren().add(loading);

        javafx.concurrent.Task<java.util.List<org.bson.Document>> task = new javafx.concurrent.Task<>() {
            @Override protected java.util.List<org.bson.Document> call() {
                UserProfile me = Session.getProfile();
                if (me == null) return new java.util.ArrayList<>();
                return UserService.getPendingRequests(me.username);
            }
        };

        task.setOnSucceeded(e -> {
            if (!isUiAvailable()) {
                return;
            }
            requestListBox.getChildren().clear();
            java.util.List<org.bson.Document> reqs = task.getValue();

            if (reqs.isEmpty()) {
                Label emptyReqLabel = new Label("No pending friend requests.");
                emptyReqLabel.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 12px; -fx-opacity: 0.64; -fx-padding: 4 2 4 2;");
                requestListBox.getChildren().add(emptyReqLabel);
                return;
            }

            for (org.bson.Document req : reqs) {
                String senderUsername = req.getString("sender");
                org.bson.Document senderDoc = UserService.getUserByUsername(senderUsername);
                if (senderDoc == null) continue;

                String dbName = senderDoc.getString("name");
                String profilePicUrl = senderDoc.getString("profilePic");

                javafx.scene.layout.HBox card = new javafx.scene.layout.HBox();
                card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                card.setSpacing(10);
                card.setStyle("-fx-background-color: transparent; -fx-padding: 10 4 10 4; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0;");

                javafx.scene.image.ImageView imgView = new javafx.scene.image.ImageView();
                imgView.setFitWidth(40); imgView.setFitHeight(40);
                javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(20, 20, 20);
                imgView.setClip(clip);
                if (profilePicUrl != null && profilePicUrl.startsWith("http")) {
                    imgView.setImage(new javafx.scene.image.Image(profilePicUrl, true));
                } else {
                    try { imgView.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream("/assets/default_avatar.png"))); } catch (Exception ex) {}
                }

                javafx.scene.layout.VBox nameBox = new javafx.scene.layout.VBox(2);
                Label nameLabel = new Label(dbName);
                nameLabel.setStyle("-fx-text-fill: -lm-text; -fx-font-size: 13.5px; -fx-font-weight: 800;");
                Label usernameLabel = new Label("@" + senderUsername);
                usernameLabel.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 11px;");
                nameBox.getChildren().addAll(nameLabel, usernameLabel);

                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                Button acceptBtn = new Button("Accept");
                acceptBtn.setStyle("-fx-background-color: #16A34A; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 5 10; -fx-cursor: hand; -fx-font-weight: bold;");

                Button rejectBtn = new Button("X");
                rejectBtn.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 5 8; -fx-cursor: hand; -fx-font-weight: bold;");

                acceptBtn.setOnAction(ev -> handleReq(senderUsername, true, card));
                rejectBtn.setOnAction(ev -> handleReq(senderUsername, false, card));

                card.getChildren().addAll(imgView, nameBox, spacer, acceptBtn, rejectBtn);
                card.setOnMouseEntered(ev -> card.setStyle("-fx-background-color: -lm-hover; -fx-padding: 10 8 10 8; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0; -fx-background-radius: 10;"));
                card.setOnMouseExited(ev -> card.setStyle("-fx-background-color: transparent; -fx-padding: 10 4 10 4; -fx-border-color: -lm-border; -fx-border-width: 0 0 1 0;"));

                requestListBox.getChildren().add(card);
            }
        });

        Thread requestThread = new Thread(task, "load-requests");
        requestThread.setDaemon(true);
        requestThread.start();
    }

    private void handleReq(String senderUsername, boolean accept, javafx.scene.layout.HBox card) {
        javafx.concurrent.Task<Boolean> task = new javafx.concurrent.Task<>() {
            @Override protected Boolean call() {
                UserProfile me = Session.getProfile();
                return UserService.respondToRequest(senderUsername, me.username, accept);
            }
        };
        task.setOnSucceeded(e -> {
            if (!isUiAvailable()) {
                return;
            }
            if (task.getValue()) {
                requestListBox.getChildren().remove(card);
                if (requestListBox.getChildren().isEmpty()) {
                    Label emptyReqLabel = new Label("No pending friend requests.");
                    emptyReqLabel.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 12px; -fx-opacity: 0.64; -fx-padding: 4 2 4 2;");
                    requestListBox.getChildren().add(emptyReqLabel);
                }

                if (accept) {
                    loadMyFriends();
                }
            }
        });
        Thread requestActionThread = new Thread(task, "friend-request-action");
        requestActionThread.setDaemon(true);
        requestActionThread.start();
    }

    private void viewUserProfile(String username) {
        active = false;
        stopLiveUpdate();
        try {

            ViewProfileController.targetUsername = username;
            ViewProfileController.returnTarget = ViewProfileController.ReturnTarget.FRIENDS;

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view_profile.fxml"));
            Parent root = loader.load();

            Scene scene = searchUserField.getScene();
            scene.setRoot(root);
            ThemeManager.applyMainTheme(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startLiveUpdate() {
        if (!isUiAvailable()) {
            return;
        }
        keepListening = true;
        liveUpdateThread = new Thread(() -> {
            while (keepListening) {
                try {
                    UserProfile me = Session.getProfile();
                    if (me != null) {

                        java.util.List<org.bson.Document> reqs = UserService.getPendingRequests(me.username);
                        java.util.List<org.bson.Document> friends = UserService.getMyFriendsList(me.username);

                        int currentReqCount = reqs.size();
                        int currentFriendCount = friends.size();

                        if (currentReqCount != lastReqCount || currentFriendCount != lastFriendCount) {
                            lastReqCount = currentReqCount;
                            lastFriendCount = currentFriendCount;

                            Platform.runLater(() -> {
                                if (!isUiAvailable()) {
                                    return;
                                }
                                loadPendingRequests();
                                loadMyFriends();

                                String currentSearch = searchUserField.getText().trim();
                                if (!currentSearch.isEmpty()) {
                                    performSearch(currentSearch);
                                }
                            });
                        }
                    }
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "friends-live-update");
        liveUpdateThread.setDaemon(true);
        liveUpdateThread.start();
    }

    private void stopLiveUpdate() {
        keepListening = false;
        if (liveUpdateThread != null) {
            liveUpdateThread.interrupt();
        }
    }

    private boolean isUiAvailable() {
        return active && searchUserField != null;
    }
}
