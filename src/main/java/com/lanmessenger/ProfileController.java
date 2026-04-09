package com.lanmessenger;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

public class ProfileController {

    @FXML private ImageView profileImageView;
    @FXML private TextField nameField;
    @FXML private TextField usernameField;
    @FXML private DatePicker birthdatePicker;
    @FXML private Label statusLabel;
    @FXML private Button saveBtn;
    @FXML private VBox cardBox;
    @FXML private VBox profileMomentsBox;

    private String uploadedImageUrl;

    @FXML
    public void initialize() {
        SceneNavigator.playEntrance(cardBox);
        applyRoundProfileImage();
        populateProfile(Session.getProfile());
        loadMyMoments();
    }

    @FXML
    public void onBack(ActionEvent event) {
        try {
            Scene scene = nameField.getScene();
            if (scene == null) {
                return;
            }

            String cssPath = MainApp.currentTheme == MainApp.Theme.DARK ? "/main_dark.css" : "/main.css";
            SceneNavigator.swapRootWithFade(scene, "/main.fxml", cssPath);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void onChangePicture(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Picture");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File selectedFile = fileChooser.showOpenDialog(getOwnerWindow());
        if (selectedFile == null) {
            return;
        }

        profileImageView.setImage(new Image(selectedFile.toURI().toString(), true));
        setBusyStatus("Uploading picture...", "#2563EB");
        saveBtn.setDisable(true);

        Task<String> uploadTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
                return ImgBbService.uploadImage(fileBytes).imageUrl;
            }
        };

        uploadTask.setOnSucceeded(e -> {
            uploadedImageUrl = uploadTask.getValue();
            setBusyStatus("Picture uploaded successfully.", "#16A34A");
            saveBtn.setDisable(false);
        });

        uploadTask.setOnFailed(e -> {
            setBusyStatus("Failed to upload picture.", "#DC2626");
            populateProfile(Session.getProfile());
            saveBtn.setDisable(false);
        });

        Thread uploadThread = new Thread(uploadTask, "profile-picture-upload");
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    @FXML
    public void onSaveProfile(ActionEvent event) {
        UserProfile currentProfile = Session.getProfile();
        if (currentProfile == null) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String newUsername = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String birthdateStr = birthdatePicker.getValue() == null ? "" : birthdatePicker.getValue().toString();

        if (!newUsername.matches("^[A-Za-z0-9_]+$")) {
            setBusyStatus("Invalid Username (Use A-Z, 0-9, _)", "#DC2626");
            return;
        }

        boolean usernameChanged = !newUsername.equals(currentProfile.username);
        if (usernameChanged) {
            showUsernameChangeConfirmation(currentProfile, name, newUsername, birthdateStr);
        } else {
            performSave(currentProfile, name, newUsername, birthdateStr, false);
        }
    }

    private void showUsernameChangeConfirmation(UserProfile profile, String name, String newUsername, String birthdateStr) {
        ThemedDialogs.showConfirmation(
                getOwnerWindow(),
                "Profile Update",
                "Change Username?",
                "You will be logged out and need to login again to change your username.",
                "Cancel",
                "Update and\nLog Out",
                true,
                () -> performSave(profile, name, newUsername, birthdateStr, true)
        );
    }

    private void performSave(UserProfile currentProfile, String name, String newUsername, String birthdateStr, boolean usernameChanged) {
        LocalDate selectedBirthdate = birthdatePicker.getValue();
        String finalPic = resolveProfilePicture(currentProfile);
        UserProfile[] refreshedHolder = new UserProfile[1];

        setBusyStatus("Saving...", "#2563EB");
        saveBtn.setDisable(true);

        Task<Boolean> saveTask = new Task<>() {
            @Override
            protected Boolean call() {
                if (usernameChanged && UserService.isUsernameTakenByOther(newUsername, currentProfile.email)) {
                    updateMessage("Username already in use!");
                    return false;
                }

                boolean ok = UserService.updateProfileInfo(currentProfile.email, newUsername, name, birthdateStr, finalPic);
                if (!ok) {
                    updateMessage("Could not update the profile.");
                    return false;
                }

                if (usernameChanged) {
                    UserService.migrateUserData(currentProfile.username, newUsername);
                }

                UserProfile refreshed = UserService.loadRequiredProfile(currentProfile.localId, currentProfile.email);
                if (refreshed == null) {
                    refreshed = new UserProfile(currentProfile.localId, name, currentProfile.email, newUsername, selectedBirthdate, finalPic);
                    UserProfileStore.saveIdentity(currentProfile.localId, name, currentProfile.email);
                    UserProfileStore.updateUsername(currentProfile.localId, newUsername);
                    UserProfileStore.updateBirthdate(currentProfile.localId, selectedBirthdate);
                    UserProfileStore.updateProfilePic(currentProfile.localId, finalPic);
                }
                refreshedHolder[0] = refreshed;
                return true;
            }
        };

        saveTask.setOnSucceeded(ev -> {
            saveBtn.setDisable(false);
            if (!saveTask.getValue()) {
                setBusyStatus(saveTask.getMessage(), "#DC2626");
                return;
            }

            if (usernameChanged) {
                Session.clear();
                goToLogin();
                return;
            }

            Session.setProfile(refreshedHolder[0]);
            populateProfile(refreshedHolder[0]);
            loadMyMoments();
            setBusyStatus("Profile Updated!", "#10B981");
        });

        saveTask.setOnFailed(ev -> {
            saveBtn.setDisable(false);
            setBusyStatus("Failed to save profile.", "#DC2626");
        });

        Thread saveThread = new Thread(saveTask, "profile-save");
        saveThread.setDaemon(true);
        saveThread.start();
    }

    private void goToLogin() {
        try {
            Scene scene = statusLabel == null ? null : statusLabel.getScene();
            if (scene != null) {
                SceneNavigator.swapRootWithFade(scene, "/login.fxml", "/login.css");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadMyMoments() {
        if (profileMomentsBox == null) {
            return;
        }

        UserProfile me = Session.getProfile();
        if (me == null || me.username == null) {
            return;
        }

        profileMomentsBox.getChildren().clear();
        List<Document> moments = MomentsService.getUserMoments(me.username);
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
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button menuButton = MomentMenuSupport.createMenuButton(
                () -> editMomentCaption(moment),
                () -> changeMomentPicture(moment),
                () -> deleteMoment(moment)
        );
        header.getChildren().addAll(timeLabel, spacer, menuButton);
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
            setBusyStatus("Could not edit that moment.", "#DC2626");
            return;
        }

        MomentEditorDialogs.showCaptionEditor(getOwnerWindow(), moment.getString("text"), newCaption ->
                runMomentAction(
                        "Saving caption...",
                        "Caption updated.",
                        "Could not update the caption.",
                        () -> MomentsService.updateMomentCaption(momentId, newCaption)
                )
        );
    }

    private void changeMomentPicture(Document moment) {
        String momentId = getMomentId(moment);
        if (momentId == null) {
            setBusyStatus("Could not change that picture.", "#DC2626");
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
                "Uploading new moment picture...",
                "Moment picture updated.",
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
            setBusyStatus("Could not delete that moment.", "#DC2626");
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
                        "Deleting moment...",
                        "Moment deleted.",
                        "Could not delete the moment.",
                        () -> MomentsService.deleteMoment(momentId)
                )
        );
    }

    private void runMomentAction(String busyText, String successText, String failureText, Callable<Boolean> action) {
        setBusyStatus(busyText, "#2563EB");

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return action.call();
            }
        };

        task.setOnSucceeded(e -> {
            if (task.getValue()) {
                loadMyMoments();
                setBusyStatus(successText, "#10B981");
            } else {
                setBusyStatus(failureText, "#DC2626");
            }
        });

        task.setOnFailed(e -> setBusyStatus(failureText, "#DC2626"));

        Thread thread = new Thread(task, "profile-moment-action");
        thread.setDaemon(true);
        thread.start();
    }

    private void populateProfile(UserProfile profile) {
        if (profile == null) {
            return;
        }

        nameField.setText(profile.name);
        usernameField.setText(profile.username);
        birthdatePicker.setValue(profile.birthdate);
        uploadedImageUrl = profile.profilePic;
        applyProfileImage(profile.profilePic);
    }

    private void applyProfileImage(String imageUrl) {
        applyRoundProfileImage();
        if (imageUrl != null && imageUrl.startsWith("http")) {
            profileImageView.setImage(new Image(imageUrl, true));
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

    private String resolveProfilePicture(UserProfile profile) {
        if (uploadedImageUrl != null && !uploadedImageUrl.isBlank()) {
            return uploadedImageUrl;
        }
        return profile == null ? "default_avatar.png" : profile.profilePic;
    }

    private String getMomentId(Document moment) {
        return moment != null && moment.getObjectId("_id") != null ? moment.getObjectId("_id").toString() : null;
    }

    private Window getOwnerWindow() {
        return statusLabel == null || statusLabel.getScene() == null ? null : statusLabel.getScene().getWindow();
    }

    private void setBusyStatus(String text, String color) {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(text == null ? "" : text);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
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
