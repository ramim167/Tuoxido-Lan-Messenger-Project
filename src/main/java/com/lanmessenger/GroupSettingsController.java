package com.lanmessenger;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import org.bson.Document;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GroupSettingsController {
    public static String targetGroupId;

    @FXML private ScrollPane rootScroll;
    @FXML private VBox mainContent;
    @FXML private ImageView groupImageView;
    @FXML private ImageView groupPhotoCardImageView;
    @FXML private Label groupImagePlaceholder;
    @FXML private Label groupPhotoCardPlaceholder;
    @FXML private Label groupNameLabel;
    @FXML private Label groupPhotoNameLabel;
    @FXML private TextField groupNameField;
    @FXML private Label photoStatusLabel;
    @FXML private VBox friendCandidatesBox;
    @FXML private VBox membersBox;
    @FXML private Label statusLabel;
    @FXML private Button saveBtn;

    private final Map<CheckBox, String> candidateSelections = new LinkedHashMap<>();
    private File selectedGroupPhotoFile;
    private Document currentGroup;

    @FXML
    public void initialize() {
        SceneNavigator.playEntrance(mainContent);
        if (rootScroll != null) {
            SmoothScrollUtil.apply(rootScroll);
        }
        if (groupNameField != null) {
            groupNameField.textProperty().addListener((obs, oldValue, newValue) -> {
                String safeName = newValue == null ? "" : newValue.trim();
                if (groupNameLabel != null) {
                    groupNameLabel.setText(safeName);
                }
                if (groupPhotoNameLabel != null) {
                    groupPhotoNameLabel.setText(safeName);
                }
            });
        }
        loadGroupData();
    }

    @FXML
    public void onBack(ActionEvent event) {
        if (targetGroupId != null && !targetGroupId.isBlank()) {
            MainController.pendingOpenChatUsername = targetGroupId;
        }

        try {
            Scene scene = saveBtn == null ? null : saveBtn.getScene();
            if (scene != null) {
                String cssPath = MainApp.currentTheme == MainApp.Theme.DARK ? "/main_dark.css" : "/main.css";
                SceneNavigator.swapRootWithFade(scene, "/main.fxml", cssPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void onChooseGroupPhoto(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Group Picture");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(saveBtn == null || saveBtn.getScene() == null ? null : saveBtn.getScene().getWindow());
        if (file == null) {
            return;
        }

        selectedGroupPhotoFile = file;
        Image preview = new Image(file.toURI().toString(), true);
        applyGroupImages(preview, true);
        photoStatusLabel.setText(file.getName());
    }

    @FXML
    public void onSaveChanges(ActionEvent event) {
        UserProfile me = Session.getProfile();
        if (me == null || currentGroup == null || targetGroupId == null || targetGroupId.isBlank()) {
            setStatus("Could not load the group.", "#DC2626");
            return;
        }

        String newGroupName = groupNameField.getText() == null ? "" : groupNameField.getText().trim();
        List<String> selectedNewMembers = new ArrayList<>();
        for (Map.Entry<CheckBox, String> entry : candidateSelections.entrySet()) {
            if (entry.getKey().isSelected()) {
                selectedNewMembers.add(entry.getValue());
            }
        }

        String currentName = safe(currentGroup.getString("groupName"));
        boolean nameChanged = !newGroupName.equals(currentName);
        boolean photoChanged = selectedGroupPhotoFile != null;
        boolean membersChanged = !selectedNewMembers.isEmpty();

        if (!nameChanged && !photoChanged && !membersChanged) {
            setStatus("No changes to save.", "#64748B");
            return;
        }

        saveBtn.setDisable(true);
        setStatus("Saving changes...", "#2563EB");

        Task<SaveResult> saveTask = new Task<>() {
            @Override
            protected SaveResult call() throws Exception {
                SaveResult result = new SaveResult();
                result.nameChanged = nameChanged;
                result.photoChanged = photoChanged;

                if (nameChanged && !GroupService.updateGroupName(targetGroupId, newGroupName)) {
                    throw new IllegalStateException("Could not update the group name.");
                }

                if (photoChanged) {
                    byte[] bytes = Files.readAllBytes(selectedGroupPhotoFile.toPath());
                    String uploadedUrl = ImgBbService.uploadImage(bytes).imageUrl;
                    if (!GroupService.updateGroupPhoto(targetGroupId, uploadedUrl)) {
                        throw new IllegalStateException("Could not update the group photo.");
                    }
                }

                if (membersChanged) {
                    result.addedMembers = GroupService.addMembers(targetGroupId, selectedNewMembers);
                }

                if (nameChanged) {
                    MessageService.sendSystemMessage(targetGroupId, "changed the group name", me.username);
                }
                if (photoChanged) {
                    MessageService.sendSystemMessage(targetGroupId, "changed the group photo", me.username);
                }
                for (String addedUsername : result.addedMembers) {
                    MessageService.sendGroupMemberAddedSystemMessage(targetGroupId, me.username, addedUsername);
                }

                result.group = GroupService.getGroupById(targetGroupId);
                return result;
            }
        };

        saveTask.setOnSucceeded(e -> {
            saveBtn.setDisable(false);
            SaveResult result = saveTask.getValue();
            currentGroup = result.group;
            selectedGroupPhotoFile = null;
            loadGroupData();
            setStatus(buildSuccessMessage(result), "#16A34A");
        });

        saveTask.setOnFailed(e -> {
            saveBtn.setDisable(false);
            Throwable ex = saveTask.getException();
            setStatus(ex == null ? "Could not save the group changes." : ex.getMessage(), "#DC2626");
        });

        Thread thread = new Thread(saveTask, "group-settings-save");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadGroupData() {
        currentGroup = GroupService.getGroupById(targetGroupId);
        if (currentGroup == null) {
            setStatus("Group not found.", "#DC2626");
            if (saveBtn != null) {
                saveBtn.setDisable(true);
            }
            return;
        }

        String groupName = safe(currentGroup.getString("groupName"));
        String groupPic = safe(currentGroup.getString("groupPic"));

        groupNameLabel.setText(groupName);
        groupPhotoNameLabel.setText(groupName);
        groupNameField.setText(groupName);
        photoStatusLabel.setText(selectedGroupPhotoFile == null ? "No new photo selected" : selectedGroupPhotoFile.getName());
        applyGroupImages(groupPic, true);

        rebuildCandidateList();
        rebuildMembersList();
    }

    private void rebuildCandidateList() {
        friendCandidatesBox.getChildren().clear();
        candidateSelections.clear();

        UserProfile me = Session.getProfile();
        if (me == null || me.username == null || currentGroup == null) {
            return;
        }

        Set<String> currentMembers = new LinkedHashSet<>();
        List<String> members = currentGroup.getList("members", String.class);
        if (members != null) {
            currentMembers.addAll(members);
        }

        List<Document> myFriends = UserService.getMyFriendsList(me.username);
        boolean hasCandidates = false;
        for (Document friend : myFriends) {
            String username = friend.getString("username");
            if (username == null || currentMembers.contains(username)) {
                continue;
            }

            hasCandidates = true;
            String displayName = safe(friend.getString("name"));
            if (displayName.isBlank()) {
                displayName = username;
            }
            CheckBox checkBox = new CheckBox(displayName + " (@" + username + ")");
            checkBox.setWrapText(true);
            checkBox.setMaxWidth(Double.MAX_VALUE);
            checkBox.setStyle(
                    "-fx-text-fill: -lm-text;" +
                    "-fx-font-size: 13px;" +
                    "-fx-font-weight: 700;" +
                    "-fx-padding: 6 2 6 2;"
            );
            candidateSelections.put(checkBox, username);
            friendCandidatesBox.getChildren().add(checkBox);
        }

        if (!hasCandidates) {
            Label empty = new Label("No friends available to add.");
            empty.getStyleClass().add("moments-empty");
            friendCandidatesBox.getChildren().add(empty);
        }
    }

    private void rebuildMembersList() {
        membersBox.getChildren().clear();

        if (currentGroup == null) {
            return;
        }

        List<String> members = currentGroup.getList("members", String.class);
        if (members == null || members.isEmpty()) {
            Label empty = new Label("No members found.");
            empty.getStyleClass().add("moments-empty");
            membersBox.getChildren().add(empty);
            return;
        }

        String creator = safe(currentGroup.getString("creator"));
        for (String username : members) {
            membersBox.getChildren().add(buildMemberRow(username, creator.equals(username)));
        }
    }

    private HBox buildMemberRow(String username, boolean creator) {
        Document userDoc = UserService.getUserByUsername(username);
        String displayName = userDoc == null ? username : safe(userDoc.getString("name"));
        if (displayName.isBlank()) {
            displayName = username;
        }
        String profilePic = userDoc == null ? "" : safe(userDoc.getString("profilePic"));

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 12, 10, 12));
        row.setStyle(
                "-fx-background-color: rgba(255,255,255,0.06);" +
                "-fx-background-radius: 14;" +
                "-fx-border-color: -lm-border;" +
                "-fx-border-radius: 14;" +
                "-fx-border-width: 1;"
        );

        ImageView avatar = new ImageView();
        avatar.setFitWidth(38);
        avatar.setFitHeight(38);
        avatar.setPreserveRatio(true);
        avatar.setClip(new Circle(19, 19, 19));
        applyUserImage(avatar, profilePic);

        Label nameLabel = new Label(displayName);
        nameLabel.setStyle("-fx-text-fill: -lm-text; -fx-font-size: 13.5px; -fx-font-weight: 800;");

        Label usernameLabel = new Label("@" + username);
        usernameLabel.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 11.5px; -fx-font-weight: 700;");

        VBox textBox = new VBox(2, nameLabel, usernameLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(avatar, textBox, spacer);
        if (creator) {
            Label creatorLabel = new Label("Creator");
            creatorLabel.setStyle(
                    "-fx-background-color: rgba(37,99,235,0.14);" +
                    "-fx-text-fill: #2563EB;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 4 10;" +
                    "-fx-font-size: 11px;" +
                    "-fx-font-weight: 900;"
            );
            row.getChildren().add(creatorLabel);
        }

        return row;
    }

    private void applyGroupImages(String imageUrl, boolean refreshNameCard) {
        if (selectedGroupPhotoFile != null) {
            Image image = new Image(selectedGroupPhotoFile.toURI().toString(), true);
            applyGroupImages(image, refreshNameCard);
            return;
        }

        if (imageUrl != null && !imageUrl.isBlank() && imageUrl.startsWith("http")) {
            applyGroupImages(new Image(imageUrl, true), refreshNameCard);
            return;
        }

        groupImageView.setImage(null);
        groupImageView.setVisible(false);
        groupImagePlaceholder.setVisible(true);

        groupPhotoCardImageView.setImage(null);
        groupPhotoCardImageView.setVisible(false);
        groupPhotoCardPlaceholder.setVisible(true);
    }

    private void applyGroupImages(Image image, boolean refreshNameCard) {
        groupImageView.setImage(image);
        groupImageView.setVisible(true);
        groupImagePlaceholder.setVisible(false);

        groupPhotoCardImageView.setImage(image);
        groupPhotoCardImageView.setVisible(true);
        groupPhotoCardPlaceholder.setVisible(false);

        if (refreshNameCard) {
            groupPhotoNameLabel.setText(groupNameField.getText() == null ? "" : groupNameField.getText().trim());
        }
    }

    private void applyUserImage(ImageView avatar, String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("http")) {
            avatar.setImage(new Image(imageUrl, true));
            return;
        }

        try {
            avatar.setImage(new Image(getClass().getResourceAsStream("/assets/default_avatar.png")));
        } catch (Exception ignored) {
        }
    }

    private void setStatus(String text, String color) {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(text == null ? "" : text);
        statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: 800;");
    }

    private String buildSuccessMessage(SaveResult result) {
        List<String> parts = new ArrayList<>();
        if (result.nameChanged) {
            parts.add("group name updated");
        }
        if (result.photoChanged) {
            parts.add("group photo updated");
        }
        if (!result.addedMembers.isEmpty()) {
            parts.add(result.addedMembers.size() + " member(s) added");
        }
        return parts.isEmpty() ? "Changes saved." : "Saved: " + String.join(", ", parts) + ".";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class SaveResult {
        private boolean nameChanged;
        private boolean photoChanged;
        private List<String> addedMembers = new ArrayList<>();
        private Document group;
    }
}
