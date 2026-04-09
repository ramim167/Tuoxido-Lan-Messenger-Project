package com.lanmessenger;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.bson.Document;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class MomentsController {

    private static final int MAX_TEXT = 300;
    private static final int MAX_IMAGE_BYTES = 800 * 1024;
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    @FXML private ImageView myAvatar;
    @FXML private TextArea momentText;
    @FXML private Label charCountLabel;
    @FXML private VBox momentsFeed;
    @FXML private StackPane momentsDetailOverlay;
    @FXML private VBox momentsDetailContent;
    @FXML private HBox momentImagePreviewRow;
    @FXML private ImageView momentImagePreview;
    @FXML private Label selectedImageLabel;
    @FXML private Button postMomentBtn;

    @FXML
    private ScrollPane momentsScrollPane;

    private File selectedImageFile;
    private Timeline refreshTimeline;
    private boolean detailOpen = false;
    private String currentDetailMomentId;

    @FXML
    public void initialize() {
        setupComposer();
        loadFeed();
        setupDetailOverlay();
        startRefreshLoop();
        if (momentsScrollPane != null) {
            SmoothScrollUtil.apply(momentsScrollPane);
        }
    }

    private void setupComposer() {
        updateAvatar();
        if (momentText != null) {
            momentText.textProperty().addListener((obs, oldV, newV) -> {
                if (newV != null && newV.length() > MAX_TEXT) {
                    momentText.setText(newV.substring(0, MAX_TEXT));
                }
                updateCharCount();
                updatePostState();
            });
        }
        if (momentImagePreviewRow != null) {
            momentImagePreviewRow.setVisible(false);
            momentImagePreviewRow.setManaged(false);
        }
        updateCharCount();
        updatePostState();
    }

    private void setupDetailOverlay() {
        if (momentsDetailOverlay != null) {
            momentsDetailOverlay.setVisible(false);
            momentsDetailOverlay.setManaged(false);
        }
    }

    private void startRefreshLoop() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            if (!detailOpen) loadFeed();
        }));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    private void updateAvatar() {
        if (myAvatar == null) return;
        UserProfile me = Session.getProfile();
        String url = (me != null) ? me.profilePic : null;
        Image img = null;
        if (url != null && url.startsWith("http")) {
            img = getCachedImage(url);
        }
        if (img == null) {
            try {
                img = new Image(getClass().getResourceAsStream("/assets/default_avatar.png"));
            } catch (Exception ignored) { }
        }
        if (img != null) myAvatar.setImage(img);
        applyCircleClip(myAvatar, 18);
    }

    private void updateCharCount() {
        if (charCountLabel == null) return;
        int len = momentText == null || momentText.getText() == null ? 0 : momentText.getText().length();
        charCountLabel.setText(len + "/" + MAX_TEXT);
    }

    private void updatePostState() {
        if (postMomentBtn == null) return;
        boolean hasText = momentText != null && momentText.getText() != null && !momentText.getText().trim().isEmpty();
        boolean hasImage = selectedImageFile != null;
        postMomentBtn.setDisable(!(hasText || hasImage));
    }

    private Image getCachedImage(String url) {
        if (url == null) return null;
        String key = url.trim();
        if (key.isEmpty()) return null;
        return IMAGE_CACHE.computeIfAbsent(key, u -> new Image(u, true));
    }

    private void applyCircleClip(ImageView view, double radius) {
        if (view == null) return;
        Circle clip = new Circle(radius, radius, radius);
        view.setClip(clip);
    }

    @FXML
    private void onAttachMomentImage() {
        if (momentText == null) return;
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(momentText.getScene().getWindow());
        if (file == null) return;

        selectedImageFile = file;
        if (momentImagePreview != null) {
            momentImagePreview.setImage(new Image(file.toURI().toString(), true));
        }
        if (selectedImageLabel != null) {
            selectedImageLabel.setText(file.getName());
        }
        if (momentImagePreviewRow != null) {
            momentImagePreviewRow.setManaged(true);
            momentImagePreviewRow.setVisible(true);
        }
        updatePostState();
    }

    @FXML
    private void onRemoveMomentImage() {
        selectedImageFile = null;
        if (momentImagePreview != null) momentImagePreview.setImage(null);
        if (selectedImageLabel != null) selectedImageLabel.setText("No photo selected");
        if (momentImagePreviewRow != null) {
            momentImagePreviewRow.setVisible(false);
            momentImagePreviewRow.setManaged(false);
        }
        updatePostState();
    }

    @FXML
    private void onPostMoment() {
        UserProfile me = Session.getProfile();
        if (me == null) return;

        String rawText = momentText == null || momentText.getText() == null ? "" : momentText.getText().trim();
        if (rawText.length() > MAX_TEXT) rawText = rawText.substring(0, MAX_TEXT);
        if (rawText.isEmpty() && selectedImageFile == null) return;

        final String finalText = rawText;
        final File imageFile = selectedImageFile;
        final String myUsername = me.username;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String imageUrl = null;
                if (imageFile != null) {
                    byte[] data = compressImageToLimit(imageFile, MAX_IMAGE_BYTES);
                    imageUrl = ImgBbService.uploadImage(data).imageUrl;
                }
                MomentsService.createMoment(myUsername, finalText, imageUrl);
                return null;
            }
        };
        task.setOnSucceeded(ev -> {
            if (momentText != null) momentText.clear();
            onRemoveMomentImage();
            loadFeed();
        });
        task.setOnFailed(ev -> {
            ThemedDialogs.showAlert(getOwnerWindow(), "Moment", "Failed to post moment. Please try again.", true);
        });
        new Thread(task, "moment-post-thread").start();
    }

    private void loadFeed() {
        UserProfile me = Session.getProfile();
        if (me == null) return;

        Task<List<Document>> task = new Task<>() {
            @Override
            protected List<Document> call() {
                List<String> friendUsernames = new ArrayList<>();
                List<Document> friends = UserService.getMyFriendsList(me.username);
                for (Document doc : friends) {
                    String u = doc.getString("username");
                    if (u != null) friendUsernames.add(u);
                }
                return MomentsService.getFeed(me.username, friendUsernames);
            }
        };

        task.setOnSucceeded(ev -> Platform.runLater(() -> renderFeed(task.getValue())));
        new Thread(task, "moments-feed").start();
    }

    private void renderFeed(List<Document> feed) {
        if (momentsFeed == null) return;
        momentsFeed.getChildren().clear();
        if (feed == null || feed.isEmpty()) {
            Label empty = new Label("No moments yet. Share something to start the story.");
            empty.getStyleClass().add("moments-empty");
            momentsFeed.getChildren().add(empty);
            return;
        }
        for (Document moment : feed) {
            momentsFeed.getChildren().add(buildMomentCard(moment));
        }
    }

    private void openMomentDetail(String momentId) {
        if (momentId == null || momentsDetailOverlay == null || momentsDetailContent == null) return;
        detailOpen = true;
        currentDetailMomentId = momentId;
        if (refreshTimeline != null) refreshTimeline.pause();

        Task<Document> task = new Task<>() {
            @Override
            protected Document call() {
                return MomentsService.getMomentById(momentId);
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> {
            Document moment = task.getValue();
            if (moment == null) return;
            momentsDetailContent.getChildren().clear();
            momentsDetailContent.getChildren().add(buildMomentDetail(moment));
            momentsDetailOverlay.setManaged(true);
            momentsDetailOverlay.setVisible(true);
            momentsDetailOverlay.toFront();
        }));
        new Thread(task, "moment-detail").start();
    }

    @FXML
    private void onCloseMomentDetail() {
        detailOpen = false;
        currentDetailMomentId = null;
        if (momentsDetailOverlay != null) {
            momentsDetailOverlay.setVisible(false);
            momentsDetailOverlay.setManaged(false);
        }
        if (refreshTimeline != null) refreshTimeline.play();
    }

    private VBox buildMomentDetail(Document moment) {
        String username = moment.getString("username");
        String text = moment.getString("text");
        String imageUrl = moment.getString("imageUrl");
        long timestamp = moment.containsKey("timestamp") ? moment.getLong("timestamp") : Instant.now().toEpochMilli();
        String momentId = getMomentId(moment);

        Document userDoc = UserService.getUserByUsername(username);
        String displayName = userDoc != null ? userDoc.getString("name") : username;
        String profilePic = userDoc != null ? userDoc.getString("profilePic") : null;

        List<String> loves = moment.getList("likes", String.class);
        List<Document> comments = moment.getList("comments", Document.class);
        int loveCount = loves == null ? 0 : loves.size();
        int commentCount = comments == null ? 0 : comments.size();

        VBox card = new VBox(12);
        card.getStyleClass().add("moment-card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("moment-header");

        ImageView avatar = new ImageView();
        avatar.setFitWidth(40);
        avatar.setFitHeight(40);
        avatar.setPreserveRatio(true);
        if (profilePic != null && profilePic.startsWith("http")) {
            avatar.setImage(getCachedImage(profilePic));
        } else {
            try {
                avatar.setImage(new Image(getClass().getResourceAsStream("/assets/default_avatar.png")));
            } catch (Exception ignored) { }
        }
        applyCircleClip(avatar, 20);

        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(displayName);
        nameLabel.getStyleClass().add("moment-name");
        nameLabel.getStyleClass().add("profile-link");
        nameLabel.setOnMouseClicked(e -> openUserProfile(username));
        Label timeLabel = new Label(formatRelativeTime(timestamp));
        timeLabel.getStyleClass().add("moment-time");
        nameBox.getChildren().addAll(nameLabel, timeLabel);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        header.getChildren().addAll(avatar, nameBox, headerSpacer);
        if (momentId != null && isOwnMoment(username)) {
            Button menuButton = MomentMenuSupport.createMenuButton(
                    () -> editMomentCaption(moment),
                    () -> changeMomentPicture(moment),
                    () -> deleteMoment(moment)
            );
            header.getChildren().add(menuButton);
        }
        card.getChildren().add(header);

        if (text != null && !text.isBlank()) {
            Label textLabel = new Label(text);
            textLabel.setWrapText(true);
            textLabel.getStyleClass().add("moment-text");
            card.getChildren().add(textLabel);
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            ImageView imageView = new ImageView(getCachedImage(imageUrl));
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(560);
            imageView.setSmooth(true);
            imageView.getStyleClass().add("moment-image");
            card.getChildren().add(imageView);
        }

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        UserProfile me = Session.getProfile();
        boolean lovedByMe = me != null && loves != null && loves.contains(me.username);
        Button loveBtn = new Button(lovedByMe ? "Loved" : "Love");
        loveBtn.getStyleClass().add("moment-action-btn");
        if (lovedByMe) loveBtn.getStyleClass().add("liked");
        Label loveCountLabel = new Label(loveCount + " Loves");
        loveCountLabel.getStyleClass().add("moment-meta");

        Label commentCountLabel = new Label(commentCount + " Comments");
        commentCountLabel.getStyleClass().add("moment-meta");

        actions.getChildren().addAll(loveBtn, loveCountLabel, commentCountLabel);
        card.getChildren().add(actions);

        int[] loveCountHolder = new int[] { loveCount };
        loveBtn.setOnAction(e -> {
            if (me == null) return;
            boolean nowLoved = MomentsService.toggleLike(momentId, me.username);
            loveBtn.getStyleClass().remove("liked");
            if (nowLoved) {
                loveBtn.getStyleClass().add("liked");
                loveCountHolder[0] = loveCountHolder[0] + 1;
            } else {
                loveCountHolder[0] = Math.max(0, loveCountHolder[0] - 1);
            }
            loveBtn.setText(nowLoved ? "Loved" : "Love");
            loveCountLabel.setText(loveCountHolder[0] + " Loves");
            loadFeed();
        });

        VBox commentsBox = new VBox(8);
        commentsBox.getStyleClass().add("moment-comments");
        if (comments != null) {
            for (Document c : comments) {
                commentsBox.getChildren().add(buildCommentRow(c));
            }
        }
        card.getChildren().add(commentsBox);

        HBox commentInput = new HBox(8);
        commentInput.setAlignment(Pos.CENTER_LEFT);
        TextField commentField = new TextField();
        commentField.setPromptText("Write a comment...");
        commentField.getStyleClass().add("moment-comment-input");
        HBox.setHgrow(commentField, Priority.ALWAYS);
        Button postBtn = new Button("Post");
        postBtn.getStyleClass().add("moment-comment-btn");
        commentInput.getChildren().addAll(commentField, postBtn);
        card.getChildren().add(commentInput);

        Runnable postComment = () -> {
            if (me == null) return;
            String cText = commentField.getText() == null ? "" : commentField.getText().trim();
            if (cText.isEmpty()) return;
            if (cText.length() > 200) cText = cText.substring(0, 200);
            MomentsService.addComment(momentId, me.username, cText);
            openMomentDetail(momentId);
            loadFeed();
        };
        postBtn.setOnAction(e -> postComment.run());
        commentField.setOnAction(e -> postComment.run());

        return card;
    }

    private VBox buildMomentCard(Document moment) {
        String username = moment.getString("username");
        String text = moment.getString("text");
        String imageUrl = moment.getString("imageUrl");
        long timestamp = moment.containsKey("timestamp") ? moment.getLong("timestamp") : Instant.now().toEpochMilli();
        String momentId = getMomentId(moment);

        Document userDoc = UserService.getUserByUsername(username);
        String displayName = userDoc != null ? userDoc.getString("name") : username;
        String profilePic = userDoc != null ? userDoc.getString("profilePic") : null;

        List<String> likes = moment.getList("likes", String.class);
        List<Document> comments = moment.getList("comments", Document.class);
        int loveCount = likes == null ? 0 : likes.size();
        int commentCount = comments == null ? 0 : comments.size();

        boolean likedByMe = false;
        UserProfile me = Session.getProfile();
        if (me != null && likes != null) likedByMe = likes.contains(me.username);

        VBox card = new VBox(10);
        card.getStyleClass().add("moment-card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("moment-header");

        ImageView avatar = new ImageView();
        avatar.setFitWidth(38);
        avatar.setFitHeight(38);
        avatar.setPreserveRatio(true);
        if (profilePic != null && profilePic.startsWith("http")) {
            avatar.setImage(getCachedImage(profilePic));
        } else {
            try {
                avatar.setImage(new Image(getClass().getResourceAsStream("/assets/default_avatar.png")));
            } catch (Exception ignored) { }
        }
        applyCircleClip(avatar, 19);

        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(displayName);
        nameLabel.getStyleClass().add("moment-name");
        nameLabel.getStyleClass().add("profile-link");
        nameLabel.setOnMouseClicked(e -> openUserProfile(username));
        Label timeLabel = new Label(formatRelativeTime(timestamp));
        timeLabel.getStyleClass().add("moment-time");
        nameBox.getChildren().addAll(nameLabel, timeLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(avatar, nameBox, spacer);
        if (momentId != null && isOwnMoment(username)) {
            Button menuButton = MomentMenuSupport.createMenuButton(
                    () -> editMomentCaption(moment),
                    () -> changeMomentPicture(moment),
                    () -> deleteMoment(moment)
            );
            header.getChildren().add(menuButton);
        }
        card.getChildren().add(header);

        if (text != null && !text.isBlank()) {
            Label textLabel = new Label(text);
            textLabel.setWrapText(true);
            textLabel.getStyleClass().add("moment-text");
            card.getChildren().add(textLabel);
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            ImageView imageView = new ImageView(getCachedImage(imageUrl));
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(520);
            imageView.setSmooth(true);
            imageView.getStyleClass().add("moment-image");
            card.getChildren().add(imageView);
        }

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(4, 0, 0, 0));

        Button loveBtn = new Button(likedByMe ? "Loved" : "Love");
        loveBtn.getStyleClass().add("moment-action-btn");
        if (likedByMe) loveBtn.getStyleClass().add("liked");
        Label loveCountLabel = new Label(loveCount + " Loves");
        loveCountLabel.getStyleClass().add("moment-meta");

        Button commentBtn = new Button("Comments");
        commentBtn.getStyleClass().add("moment-action-btn");
        Label commentCountLabel = new Label(commentCount + " Comments");
        commentCountLabel.getStyleClass().add("moment-meta");

        actions.getChildren().addAll(loveBtn, loveCountLabel, commentBtn, commentCountLabel);
        card.getChildren().add(actions);

        int[] loveCountHolder = new int[] { loveCount };
        loveBtn.setOnAction(e -> {
            if (me == null) return;
            boolean nowLiked = MomentsService.toggleLike(momentId, me.username);
            loveBtn.getStyleClass().remove("liked");
            if (nowLiked) {
                loveBtn.getStyleClass().add("liked");
                loveCountHolder[0] = loveCountHolder[0] + 1;
            } else {
                loveCountHolder[0] = Math.max(0, loveCountHolder[0] - 1);
            }
            loveBtn.setText(nowLiked ? "Loved" : "Love");
            loveCountLabel.setText(loveCountHolder[0] + " Loves");
        });
        commentBtn.setOnAction(e -> openMomentDetail(momentId));
        commentCountLabel.setOnMouseClicked(e -> openMomentDetail(momentId));

        return card;
    }

    private HBox buildCommentRow(Document comment) {
        String username = comment.getString("username");
        String text = comment.getString("text");

        Document userDoc = UserService.getUserByUsername(username);
        String displayName = userDoc != null ? userDoc.getString("name") : username;
        String profilePic = userDoc != null ? userDoc.getString("profilePic") : null;

        HBox row = new HBox(6);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("moment-comment-row");

        Label nameLabel = new Label(displayName);
        nameLabel.getStyleClass().add("moment-comment-name");
        nameLabel.getStyleClass().add("profile-link");
        nameLabel.setOnMouseClicked(e -> openUserProfile(username));

        Label textLabel = new Label(text == null ? "" : text);
        textLabel.setWrapText(true);
        textLabel.getStyleClass().add("moment-comment-text");

        VBox textBox = new VBox(2, nameLabel, textLabel);
        textBox.setAlignment(Pos.TOP_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ImageView avatar = new ImageView();
        avatar.setFitWidth(22);
        avatar.setFitHeight(22);
        avatar.setPreserveRatio(true);
        if (profilePic != null && profilePic.startsWith("http")) {
            avatar.setImage(getCachedImage(profilePic));
        } else {
            try {
                avatar.setImage(new Image(getClass().getResourceAsStream("/assets/default_avatar.png")));
            } catch (Exception ignored) { }
        }
        applyCircleClip(avatar, 11);
        avatar.getStyleClass().add("moment-comment-avatar");

        row.getChildren().addAll(textBox, spacer, avatar);
        return row;
    }

    private void editMomentCaption(Document moment) {
        String momentId = getMomentId(moment);
        if (momentId == null) {
            showMomentError("Could not edit that moment.");
            return;
        }

        MomentEditorDialogs.showCaptionEditor(getOwnerWindow(), moment.getString("text"), newCaption ->
                runMomentMutation(
                        "Could not update the caption.",
                        () -> MomentsService.updateMomentCaption(momentId, newCaption),
                        () -> refreshMomentViews(momentId)
                )
        );
    }

    private void changeMomentPicture(Document moment) {
        String momentId = getMomentId(moment);
        if (momentId == null) {
            showMomentError("Could not change that picture.");
            return;
        }

        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select Moment Picture");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        File file = chooser.showOpenDialog(getOwnerWindow());
        if (file == null) {
            return;
        }

        runMomentMutation(
                "Could not update the moment picture.",
                () -> {
                    byte[] data = compressImageToLimit(file, MAX_IMAGE_BYTES);
                    String imageUrl = ImgBbService.uploadImage(data).imageUrl;
                    return MomentsService.updateMomentImage(momentId, imageUrl);
                },
                () -> refreshMomentViews(momentId)
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
                () -> runMomentMutation(
                        "Could not delete the moment.",
                        () -> MomentsService.deleteMoment(momentId),
                        () -> {
                            if (momentId.equals(currentDetailMomentId)) {
                                onCloseMomentDetail();
                            }
                            loadFeed();
                        }
                )
        );
    }

    private void runMomentMutation(String failureText, Callable<Boolean> action, Runnable onSuccess) {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return action.call();
            }
        };

        task.setOnSucceeded(e -> {
            if (task.getValue()) {
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } else {
                showMomentError(failureText);
            }
        });

        task.setOnFailed(e -> showMomentError(failureText));

        Thread thread = new Thread(task, "moment-mutation");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshMomentViews(String momentId) {
        loadFeed();
        if (detailOpen && momentId != null && momentId.equals(currentDetailMomentId)) {
            openMomentDetail(momentId);
        }
    }

    private boolean isOwnMoment(String username) {
        UserProfile me = Session.getProfile();
        return me != null && me.username != null && me.username.equals(username);
    }

    private String getMomentId(Document moment) {
        return moment != null && moment.getObjectId("_id") != null ? moment.getObjectId("_id").toString() : null;
    }

    private Window getOwnerWindow() {
        return momentsFeed == null || momentsFeed.getScene() == null ? null : momentsFeed.getScene().getWindow();
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

    private byte[] compressImageToLimit(File file, int maxBytes) throws Exception {
        byte[] original = Files.readAllBytes(file.toPath());
        if (original.length <= maxBytes) return original;

        BufferedImage source = ImageIO.read(file);
        if (source == null) return original;

        BufferedImage working = toRgb(source);
        int targetWidth = Math.min(working.getWidth(), 1200);
        if (targetWidth < working.getWidth()) {
            working = scaleImage(working, targetWidth);
        }

        byte[] result = writeJpeg(working, 0.9f);
        float quality = 0.9f;
        int attempts = 0;
        while (result.length > maxBytes && attempts < 14) {
            attempts++;
            if (quality > 0.35f) {
                quality -= 0.1f;
            } else {
                targetWidth = (int) (targetWidth * 0.8);
                if (targetWidth < 240) targetWidth = 240;
                working = scaleImage(working, targetWidth);
                quality = 0.9f;
            }
            result = writeJpeg(working, quality);
            if (targetWidth <= 240 && quality <= 0.35f) break;
        }
        if (result.length > maxBytes) {
            targetWidth = Math.max(200, (int) (working.getWidth() * 0.7));
            working = scaleImage(working, targetWidth);
            result = writeJpeg(working, 0.35f);
        }
        return result;
    }

    private BufferedImage toRgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) return img;
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private BufferedImage scaleImage(BufferedImage img, int width) {
        int height = (int) (img.getHeight() * (width / (double) img.getWidth()));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(img, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }

    private byte[] writeJpeg(BufferedImage img, float quality) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) return baos.toByteArray();
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Math.max(0.1f, Math.min(quality, 1.0f)));
        }
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);
        writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
        ios.close();
        writer.dispose();
        return baos.toByteArray();
    }

    private void openUserProfile(String username) {
        if (username == null || username.isBlank()) return;
        try {
            if (refreshTimeline != null) refreshTimeline.stop();
            ViewProfileController.targetUsername = username;
            ViewProfileController.returnTarget = ViewProfileController.ReturnTarget.MOMENTS;

            Stage stage = (Stage) momentsFeed.getScene().getWindow();
            Scene scene = stage.getScene();
            Parent root = new FXMLLoader(MainApp.class.getResource("/view_profile.fxml")).load();
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

    @FXML
    private void onBackToMain(javafx.event.ActionEvent event) {
        try {
            if (refreshTimeline != null) refreshTimeline.stop();
            Stage stage = (Stage) momentsFeed.getScene().getWindow();
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
}
