package com.lanmessenger;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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
import java.util.StringJoiner;
import java.util.concurrent.Callable;

public class ProfileController {
    private static final int MAX_BIO_WORDS = 150;

    @FXML private ImageView profileImageView;
    @FXML private TextField nameField;
    @FXML private TextField usernameField;
    @FXML private TextArea bioField;
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
        configureBioField();
        populateProfile(Session.getProfile());
        refreshProfileHeader();
        loadMyMoments();
    }

    @FXML
    public void onBack(ActionEvent event) {
        try {
            SceneNavigator.swapRootWithMainThemeInstant(event, nameField, "/main.fxml");
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
            if (uploadedImageUrl == null || uploadedImageUrl.isBlank()) {
                setBusyStatus("Upload completed, but image URL is missing.", "#DC2626");
                saveBtn.setDisable(false);
                return;
            }
            applyProfileImage(uploadedImageUrl);
            persistProfilePicture(uploadedImageUrl);
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
        String rawBio = bioField == null ? "" : bioField.getText();
        String bio = normalizeBio(rawBio);
        if (bioField != null && !bio.equals(bioField.getText())) {
            bioField.setText(bio);
        }
        String birthdateStr = birthdatePicker.getValue() == null ? "" : birthdatePicker.getValue().toString();

        if (!newUsername.matches("^[A-Za-z0-9_]+$")) {
            setBusyStatus("Invalid Username (Use A-Z, 0-9, _)", "#DC2626");
            return;
        }

        boolean usernameChanged = !newUsername.equals(currentProfile.username);
        if (usernameChanged) {
            showUsernameChangeConfirmation(currentProfile, name, newUsername, birthdateStr, bio);
        } else {
            performSave(currentProfile, name, newUsername, birthdateStr, bio, false);
        }
    }

    private void showUsernameChangeConfirmation(UserProfile profile, String name, String newUsername, String birthdateStr, String bio) {
        ThemedDialogs.showConfirmation(
                getOwnerWindow(),
                "Profile Update",
                "Change Username?",
                "You will be logged out and need to login again to change your username.",
                "Cancel",
                "Update and\nLog Out",
                true,
                () -> performSave(profile, name, newUsername, birthdateStr, bio, true)
        );
    }

    private void performSave(UserProfile currentProfile, String name, String newUsername, String birthdateStr, String bio, boolean usernameChanged) {
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

                boolean ok = UserService.updateProfileInfo(currentProfile.email, newUsername, name, birthdateStr, finalPic, bio);
                if (!ok) {
                    updateMessage("Could not update the profile.");
                    return false;
                }

                if (usernameChanged) {
                    UserService.migrateUserData(currentProfile.username, newUsername);
                }

                UserProfile refreshed = UserService.loadRequiredProfile(currentProfile.localId, currentProfile.email);
                if (refreshed == null) {
                    refreshed = new UserProfile(currentProfile.localId, name, currentProfile.email, newUsername, selectedBirthdate, finalPic, bio);
                    UserProfileStore.saveIdentity(currentProfile.localId, name, currentProfile.email);
                    UserProfileStore.updateUsername(currentProfile.localId, newUsername);
                    UserProfileStore.updateBirthdate(currentProfile.localId, selectedBirthdate);
                    UserProfileStore.updateProfilePic(currentProfile.localId, finalPic);
                    UserProfileStore.updateBio(currentProfile.localId, bio);
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
            String displayable = ImgBbService.toDisplayableUrl(imageUrl);
            ImageView imageView = new ImageView(new Image(displayable == null ? imageUrl : displayable, true));
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
                    String imageUrl = ImgBbService.uploadImage(bytes, 7 * 24 * 60 * 60).imageUrl;
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
        if (bioField != null) {
            bioField.setText(normalizeBio(profile.bio));
        }
        birthdatePicker.setValue(profile.birthdate);
        uploadedImageUrl = profile.profilePic;
        applyProfileImage(profile.profilePic);
    }

    private void configureBioField() {
        if (bioField == null) {
            return;
        }

        bioField.setWrapText(true);

        bioField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (countBioWords(newValue) <= MAX_BIO_WORDS) {
                return;
            }

            String limited = limitBioToWords(newValue, MAX_BIO_WORDS);
            bioField.setText(limited);
            bioField.positionCaret(limited.length());
        });
    }

    private int countBioWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private String limitBioToWords(String text, int maxWords) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String[] words = text.trim().split("\\s+");
        StringJoiner joiner = new StringJoiner(" ");

        for (int i = 0; i < Math.min(words.length, maxWords); i++) {
            joiner.add(words[i]);
        }

        return joiner.toString();
    }

    private String normalizeBio(String bio) {
        if (bio == null || bio.isBlank()) {
            return "";
        }
        String[] words = bio.trim().split("\\s+");
        int limit = Math.min(words.length, MAX_BIO_WORDS);
        StringJoiner joiner = new StringJoiner(" ");
        for (int i = 0; i < limit; i++) {
            joiner.add(words[i]);
        }
        return joiner.toString();
    }

    private void applyProfileImage(String imageUrl) {
        applyRoundProfileImage();
        Image fallback = loadDefaultAvatarImage();
        String displayableUrl = ImgBbService.toDisplayableUrl(imageUrl);
        if (displayableUrl != null && displayableUrl.startsWith("http")) {
            try {
                Image remote = new Image(displayableUrl, true);
                remote.errorProperty().addListener((obs, oldVal, hasError) -> {
                    if (Boolean.TRUE.equals(hasError) && fallback != null) {
                        profileImageView.setImage(fallback);
                    }
                });
                profileImageView.setImage(remote);
                if (remote.isError() && fallback != null) {
                    profileImageView.setImage(fallback);
                }
                return;
            } catch (Exception ignored) {
            }
        }
        if (fallback != null) {
            profileImageView.setImage(fallback);
        }
    }

    private Image loadDefaultAvatarImage() {
        try {
            return new Image(getClass().getResourceAsStream("/assets/default_avatar.png"));
        } catch (Exception ignored) {
            return null;
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

    private void persistProfilePicture(String imageUrl) {
        UserProfile current = Session.getProfile();
        if (current == null) {
            setBusyStatus("Picture uploaded. Please save profile to apply.", "#2563EB");
            saveBtn.setDisable(false);
            return;
        }

        Task<Boolean> persistTask = new Task<>() {
            @Override
            protected Boolean call() {
                String birthdate = current.birthdate == null ? "" : current.birthdate.toString();
                boolean ok = UserService.updateProfileInfo(
                        current.email,
                        current.username,
                        current.name,
                        birthdate,
                        imageUrl,
                        current.bio
                );
                if (!ok) {
                    return false;
                }
                UserProfile refreshed = UserService.loadRequiredProfile(current.localId, current.email);
                if (refreshed != null) {
                    Session.setProfile(refreshed);
                }
                MainController.invalidateCachedUserAvatar(current.username);
                return true;
            }
        };

        persistTask.setOnSucceeded(ev -> {
            saveBtn.setDisable(false);
            if (persistTask.getValue()) {
                setBusyStatus("Picture uploaded and saved.", "#16A34A");
                UserProfile active = Session.getProfile();
                if (active != null) {
                    populateProfile(active);
                }
            } else {
                setBusyStatus("Picture uploaded, but save failed. Press Save.", "#DC2626");
            }
        });
        persistTask.setOnFailed(ev -> {
            saveBtn.setDisable(false);
            setBusyStatus("Picture uploaded, but save failed. Press Save.", "#DC2626");
        });

        Thread persistThread = new Thread(persistTask, "profile-picture-save");
        persistThread.setDaemon(true);
        persistThread.start();
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

    private void refreshProfileHeader() {
        UserProfile current = Session.getProfile();
        if (current == null || current.email == null || current.email.isBlank()) {
            return;
        }

        Task<UserProfile> task = new Task<>() {
            @Override
            protected UserProfile call() {
                return UserService.loadRequiredProfile(current.localId, current.email);
            }
        };
        task.setOnSucceeded(e -> {
            UserProfile latest = task.getValue();
            if (latest != null) {
                Session.setProfile(latest);
                populateProfile(latest);
            }
        });

        Thread thread = new Thread(task, "refresh-profile-header");
        thread.setDaemon(true);
        thread.start();
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
