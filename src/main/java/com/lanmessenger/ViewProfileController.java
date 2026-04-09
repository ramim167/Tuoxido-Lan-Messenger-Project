package com.lanmessenger;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.bson.Document;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

public class ViewProfileController {

    @FXML private ImageView profileImageView;
    @FXML private Label nameLabel;
    @FXML private Label usernameLabel;
    @FXML private Label dobLabel;
    @FXML private VBox profileMomentsBox;

    public static String targetUsername = null;

    public enum ReturnTarget {MAIN, FRIENDS, MOMENTS}

    public static ReturnTarget returnTarget = ReturnTarget.MAIN;

    @FXML
    public void initialize() {
        applyRoundProfileImage();
        loadProfileHeader();
        loadProfileMoments();
    }

    @FXML
    public void onBack(ActionEvent event) {
        try {
            String fxml = "/main.fxml";
            if (returnTarget == ReturnTarget.FRIENDS) {
                fxml = "/friends.fxml";
            } else if (returnTarget == ReturnTarget.MOMENTS) {
                fxml = "/moments.fxml";
            }

            if (returnTarget == ReturnTarget.MAIN && targetUsername != null) {
                MainController.pendingOpenChatUsername = targetUsername;
            }

            SceneNavigator.swapRootWithMainTheme(event, nameLabel, fxml);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadProfileHeader() {
        if (targetUsername == null) {
            return;
        }

        Document userDoc = UserService.getUserByUsername(targetUsername);
        if (userDoc == null) {
            return;
        }

        nameLabel.setText(userDoc.getString("name"));
        usernameLabel.setText("@" + userDoc.getString("username"));

        String dob = userDoc.getString("birthdate");
        dobLabel.setText((dob != null && !dob.isEmpty()) ? dob : "Not provided");

        String pic = userDoc.getString("profilePic");
        applyRoundProfileImage();
        if (pic != null && pic.startsWith("http")) {
            profileImageView.setImage(new Image(pic, true));
            return;
        }

        try {
            profileImageView.setImage(new Image(getClass().getResourceAsStream("/assets/default_avatar.png")));
        } catch (Exception ignored) {
        }
    }

    private void applyRoundProfileImage() {
        if (profileImageView == null) {
            return;
        }
        double radius = Math.max(profileImageView.getFitWidth(), profileImageView.getFitHeight()) / 2.0;
        profileImageView.setClip(new Circle(radius, radius, radius));
    }

    private void loadProfileMoments() {
        if (profileMomentsBox == null || targetUsername == null) {
            return;
        }

        UserProfile me = Session.getProfile();
        boolean canSee = false;
        if (me != null && me.username != null) {
            if (me.username.equals(targetUsername)) {
                canSee = true;
            } else {
                List<Document> friends = UserService.getMyFriendsList(me.username);
                for (Document doc : friends) {
                    if (targetUsername.equals(doc.getString("username"))) {
                        canSee = true;
                        break;
                    }
                }
            }
        }

        profileMomentsBox.getChildren().clear();
        if (!canSee) {
            VBox card = new VBox(6);
            card.getStyleClass().add("moment-card");
            Label locked = new Label("Moments are visible to friends only.");
            locked.getStyleClass().add("moments-empty");
            Label hint = new Label("Add this user to see their moments.");
            hint.getStyleClass().add("moment-meta");
            card.getChildren().addAll(locked, hint);
            profileMomentsBox.getChildren().add(card);
            return;
        }

        List<Document> moments = MomentsService.getUserMoments(targetUsername);
        if (moments == null || moments.isEmpty()) {
            Label empty = new Label("No moments yet.");
            empty.getStyleClass().add("moments-empty");
            profileMomentsBox.getChildren().add(empty);
            return;
        }

        for (Document moment : moments) {
            profileMomentsBox.getChildren().add(buildProfileMomentCard(moment));
        }
    }

    private VBox buildProfileMomentCard(Document moment) {
        String text = moment.getString("text");
        String imageUrl = moment.getString("imageUrl");
        long timestamp = moment.containsKey("timestamp") ? moment.getLong("timestamp") : Instant.now().toEpochMilli();

        List<String> loves = moment.getList("likes", String.class);
        List<Document> comments = moment.getList("comments", Document.class);
        int loveCount = loves == null ? 0 : loves.size();
        int commentCount = comments == null ? 0 : comments.size();

        VBox card = new VBox(8);
        card.getStyleClass().add("moment-card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label timeLabel = new Label(formatRelativeTime(timestamp));
        timeLabel.getStyleClass().add("moment-time");
        header.getChildren().add(timeLabel);

        if (isOwnProfile()) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button menuButton = MomentMenuSupport.createMenuButton(
                    () -> editMomentCaption(moment),
                    () -> changeMomentPicture(moment),
                    () -> deleteMoment(moment)
            );
            header.getChildren().addAll(spacer, menuButton);
        }

        card.getChildren().add(header);

        if (text != null && !text.isBlank()) {
            Label textLabel = new Label(text);
            textLabel.setWrapText(true);
            textLabel.getStyleClass().add("moment-text");
            card.getChildren().add(textLabel);
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            ImageView imageView = new ImageView(new Image(imageUrl, true));
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(520);
            imageView.setSmooth(true);
            imageView.getStyleClass().add("moment-image");
            card.getChildren().add(imageView);
        }

        HBox meta = new HBox(10);
        meta.setAlignment(Pos.CENTER_LEFT);
        Label loveLabel = new Label(loveCount + " Loves");
        loveLabel.getStyleClass().add("moment-meta");
        Label commentLabel = new Label(commentCount + " Comments");
        commentLabel.getStyleClass().add("moment-meta");
        meta.getChildren().addAll(loveLabel, commentLabel);
        card.getChildren().add(meta);

        return card;
    }

    private void editMomentCaption(Document moment) {
        String momentId = getMomentId(moment);
        if (momentId == null) {
            showMomentError("Could not edit that moment.");
            return;
        }

        MomentEditorDialogs.showCaptionEditor(getOwnerWindow(), moment.getString("text"), newCaption ->
                runMomentAction(
                        "Could not update the caption.",
                        () -> MomentsService.updateMomentCaption(momentId, newCaption)
                )
        );
    }

    private void changeMomentPicture(Document moment) {
        String momentId = getMomentId(moment);
        if (momentId == null) {
            showMomentError("Could not change that picture.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Moment Picture");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File file = chooser.showOpenDialog(getOwnerWindow());
        if (file == null) {
            return;
        }

        runMomentAction(
                "Could not update the moment picture.",
                () -> {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    String imageUrl = ImgBbService.uploadImage(bytes).imageUrl;
                    return MomentsService.updateMomentImage(momentId, imageUrl);
                }
        );
    }

    private void deleteMoment(Document moment) {
        String momentId = getMomentId(moment);
        if (momentId == null) {
            showMomentError("Could not delete that moment.");
            return;
        }

        ThemedDialogs.showConfirmation(
                getOwnerWindow(),
                "Moment",
                "Delete this moment?",
                "This action cannot be undone.",
                "Cancel",
                "Delete",
                true,
                () -> runMomentAction(
                        "Could not delete the moment.",
                        () -> MomentsService.deleteMoment(momentId)
                )
        );
    }

    private void runMomentAction(String failureText, Callable<Boolean> action) {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return action.call();
            }
        };

        task.setOnSucceeded(e -> {
            if (task.getValue()) {
                loadProfileMoments();
            } else {
                showMomentError(failureText);
            }
        });

        task.setOnFailed(e -> showMomentError(failureText));

        Thread thread = new Thread(task, "view-profile-moment-action");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean isOwnProfile() {
        UserProfile me = Session.getProfile();
        return me != null && me.username != null && me.username.equals(targetUsername);
    }

    private String getMomentId(Document moment) {
        return moment != null && moment.getObjectId("_id") != null ? moment.getObjectId("_id").toString() : null;
    }

    private Window getOwnerWindow() {
        return nameLabel == null || nameLabel.getScene() == null ? null : nameLabel.getScene().getWindow();
    }

    private void showMomentError(String message) {
        ThemedDialogs.showAlert(getOwnerWindow(), "Moment", message, true);
    }

    private String formatRelativeTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / 60000;
        if (minutes < 1) return "Now";
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        return days + "d";
    }
}
