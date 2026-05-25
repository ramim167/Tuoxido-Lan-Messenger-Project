package com.lanmessenger;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class MomentsController {

    private static final int MAX_TEXT = 300;
    private static final int MAX_IMAGE_BYTES = 800 * 1024;
    private static final int FEED_PAGE_SIZE = 16;
    private static final double FEED_LOAD_MORE_THRESHOLD = 0.78;
    private static final int COMMENT_MAX_TEXT = 200;
    private static final double COMMENT_REPLY_INDENT = 18;
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
    @FXML
    private ScrollPane momentsDetailScrollPane;

    private File selectedImageFile;
    private Timeline refreshTimeline;
    private boolean detailOpen = false;
    private String currentDetailMomentId;
    private volatile boolean loadingInitialFeed = false;
    private volatile boolean loadingMoreFeed = false;
    private volatile boolean hasMoreFeed = true;
    private volatile long oldestFeedTimestamp = Long.MAX_VALUE;
    private volatile String oldestFeedMomentId;
    private volatile String newestFeedMomentId;
    private volatile List<String> feedFriendUsernames = List.of();
    private final List<Document> loadedFeed = new ArrayList<>();
    private final Set<String> loadedFeedIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Document> userDocCache = new ConcurrentHashMap<>();
    private final Set<String> missingUsernames = ConcurrentHashMap.newKeySet();

    private static final class FeedLoadResult {
        private final List<Document> page;
        private final List<String> friendUsernames;
        private final Map<String, Document> usersByUsername;

        private FeedLoadResult(List<Document> page, List<String> friendUsernames, Map<String, Document> usersByUsername) {
            this.page = page;
            this.friendUsernames = friendUsernames;
            this.usersByUsername = usersByUsername;
        }
    }

    private static final class FeedRefreshSnapshot {
        private final List<String> friendUsernames;
        private final String latestMomentId;

        private FeedRefreshSnapshot(List<String> friendUsernames, String latestMomentId) {
            this.friendUsernames = friendUsernames;
            this.latestMomentId = latestMomentId;
        }
    }

    private static final class MomentDetailPayload {
        private final Document moment;
        private final Map<String, Document> usersByUsername;

        private MomentDetailPayload(Document moment, Map<String, Document> usersByUsername) {
            this.moment = moment;
            this.usersByUsername = usersByUsername;
        }
    }

    @FXML
    public void initialize() {
        setupComposer();
        setupDetailOverlay();
        if (momentsScrollPane != null) {
            SmoothScrollUtil.applyFast(momentsScrollPane);
        }
        if (momentsDetailScrollPane != null) {
            SmoothScrollUtil.applyFast(momentsDetailScrollPane);
        }
        setupFeedScrollListener();
        loadFeed();
        startRefreshLoop();
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
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(8), e -> {
            if (!detailOpen) {
                refreshFeedIfNeeded();
            }
        }));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    private void setupFeedScrollListener() {
        if (momentsScrollPane == null) {
            return;
        }
        momentsScrollPane.vvalueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                return;
            }
            if (newV.doubleValue() >= FEED_LOAD_MORE_THRESHOLD) {
                loadMoreFeed();
            }
        });
    }

    private void updateAvatar() {
        if (myAvatar == null) return;
        UserProfile me = Session.getProfile();
        String url = (me != null) ? me.profilePic : null;
        setAvatarImage(myAvatar, url, 18);
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
        String key = ImgBbService.toDisplayableUrl(url);
        if (key == null) return null;
        key = key.trim();
        if (key.isEmpty() || !key.startsWith("http")) return null;
        return IMAGE_CACHE.compute(key, (k, existing) -> {
            if (existing != null && !existing.isError()) {
                return existing;
            }
            Image fresh = new Image(k, true);
            fresh.errorProperty().addListener((obs, oldVal, hasError) -> {
                if (Boolean.TRUE.equals(hasError)) {
                    IMAGE_CACHE.remove(k, fresh);
                }
            });
            return fresh;
        });
    }

    private void applyCircleClip(ImageView view, double radius) {
        if (view == null) return;
        Circle clip = new Circle(radius, radius, radius);
        view.setClip(clip);
    }

    private Image getDefaultAvatarImage() {
        try {
            return new Image(getClass().getResourceAsStream("/assets/default_avatar.png"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setAvatarImage(ImageView avatarView, String profilePicUrl, double radius) {
        if (avatarView == null) {
            return;
        }

        Image fallback = getDefaultAvatarImage();
        if (fallback != null) {
            avatarView.setImage(fallback);
        }

        Image remote = getCachedImage(profilePicUrl);
        if (remote != null) {
            avatarView.setImage(remote);
            if (remote.isError() && fallback != null) {
                avatarView.setImage(fallback);
            } else if (fallback != null) {
                remote.errorProperty().addListener((obs, oldVal, hasError) -> {
                    if (Boolean.TRUE.equals(hasError)) {
                        Platform.runLater(() -> avatarView.setImage(fallback));
                    }
                });
            }
        }

        applyCircleClip(avatarView, radius);
    }

    private Document getUserDocCached(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        Document cached = userDocCache.get(username);
        if (cached != null) {
            return cached;
        }
        if (missingUsernames.contains(username)) {
            return null;
        }

        Document fetched = UserService.getUserByUsername(username);
        if (fetched != null) {
            userDocCache.put(username, fetched);
            return fetched;
        }
        missingUsernames.add(username);
        return null;
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
                    imageUrl = ImgBbService.uploadImage(data, 7 * 24 * 60 * 60).imageUrl;
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
        loadFeedPage(true);
    }

    private void loadMoreFeed() {
        if (detailOpen) {
            return;
        }
        loadFeedPage(false);
    }

    private void loadFeedPage(boolean reset) {
        UserProfile me = Session.getProfile();
        if (me == null) {
            return;
        }

        if (reset) {
            if (loadingInitialFeed) {
                return;
            }
            loadingInitialFeed = true;
        } else {
            if (loadingMoreFeed || loadingInitialFeed || !hasMoreFeed) {
                return;
            }
            loadingMoreFeed = true;
        }

        final Long beforeTimestamp = reset || oldestFeedTimestamp == Long.MAX_VALUE ? null : oldestFeedTimestamp;
        final String beforeMomentId = reset ? null : oldestFeedMomentId;
        final List<String> friendSnapshot = reset ? List.of() : feedFriendUsernames;

        Task<FeedLoadResult> task = new Task<>() {
            @Override
            protected FeedLoadResult call() {
                List<String> friends = (reset || friendSnapshot == null || friendSnapshot.isEmpty())
                        ? resolveFriendUsernames(me.username)
                        : new ArrayList<>(friendSnapshot);
                List<Document> page = MomentsService.getFeedPage(
                        me.username,
                        friends,
                        FEED_PAGE_SIZE,
                        beforeTimestamp,
                        beforeMomentId
                );
                Map<String, Document> usersByUsername = UserService.getUsersByUsernames(collectMomentUsernames(page));
                return new FeedLoadResult(page, friends, usersByUsername);
            }
        };

        task.setOnSucceeded(ev -> {
            FeedLoadResult result = task.getValue();
            if (result != null) {
                mergeUserDocsIntoCache(result.usersByUsername);
                if (reset) {
                    feedFriendUsernames = result.friendUsernames == null ? List.of() : List.copyOf(result.friendUsernames);
                    renderFeedReset(result.page);
                } else {
                    appendFeedPage(result.page);
                }
            }
            if (reset) {
                loadingInitialFeed = false;
            } else {
                loadingMoreFeed = false;
            }
        });

        task.setOnFailed(ev -> {
            if (reset) {
                loadingInitialFeed = false;
            } else {
                loadingMoreFeed = false;
            }
        });

        Thread thread = new Thread(task, reset ? "moments-feed-initial" : "moments-feed-more");
        thread.setDaemon(true);
        thread.start();
    }

    private List<String> resolveFriendUsernames(String myUsername) {
        List<String> usernames = new ArrayList<>();
        if (myUsername == null || myUsername.isBlank()) {
            return usernames;
        }
        List<Document> friends = UserService.getMyFriendsListOptimized(myUsername);
        for (Document doc : friends) {
            String username = doc.getString("username");
            if (username != null && !username.isBlank()) {
                usernames.add(username);
            }
        }
        return usernames;
    }

    private Set<String> collectMomentUsernames(List<Document> moments) {
        Set<String> usernames = new LinkedHashSet<>();
        if (moments == null) {
            return usernames;
        }
        for (Document moment : moments) {
            String username = moment.getString("username");
            if (username != null && !username.isBlank()) {
                usernames.add(username);
            }
        }
        return usernames;
    }

    private void mergeUserDocsIntoCache(Map<String, Document> usersByUsername) {
        if (usersByUsername == null || usersByUsername.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Document> entry : usersByUsername.entrySet()) {
            String username = entry.getKey();
            Document doc = entry.getValue();
            if (username == null || username.isBlank() || doc == null) {
                continue;
            }
            userDocCache.put(username, doc);
            missingUsernames.remove(username);
        }
    }

    private void renderFeedReset(List<Document> feed) {
        if (momentsFeed == null) {
            return;
        }

        loadedFeed.clear();
        loadedFeedIds.clear();
        oldestFeedTimestamp = Long.MAX_VALUE;
        oldestFeedMomentId = null;
        newestFeedMomentId = null;
        hasMoreFeed = true;

        momentsFeed.getChildren().clear();
        if (feed == null || feed.isEmpty()) {
            hasMoreFeed = false;
            Label empty = new Label("No moments yet. Share something to start the story.");
            empty.getStyleClass().add("moments-empty");
            momentsFeed.getChildren().add(empty);
            return;
        }

        int added = 0;
        for (Document moment : feed) {
            if (registerMoment(moment)) {
                momentsFeed.getChildren().add(buildMomentCard(moment));
                added++;
            }
        }
        updateFeedCursorState();
        hasMoreFeed = feed.size() >= FEED_PAGE_SIZE && added > 0;
    }

    private void appendFeedPage(List<Document> page) {
        if (momentsFeed == null) {
            return;
        }
        if (page == null || page.isEmpty()) {
            hasMoreFeed = false;
            return;
        }

        int added = 0;
        for (Document moment : page) {
            if (registerMoment(moment)) {
                momentsFeed.getChildren().add(buildMomentCard(moment));
                added++;
            }
        }
        updateFeedCursorState();
        if (added == 0) {
            hasMoreFeed = false;
            return;
        }
        hasMoreFeed = page.size() >= FEED_PAGE_SIZE;
    }

    private boolean registerMoment(Document moment) {
        String id = getMomentId(moment);
        if (id == null || !loadedFeedIds.add(id)) {
            return false;
        }
        loadedFeed.add(moment);
        return true;
    }

    private void updateFeedCursorState() {
        if (loadedFeed.isEmpty()) {
            oldestFeedTimestamp = Long.MAX_VALUE;
            oldestFeedMomentId = null;
            newestFeedMomentId = null;
            return;
        }
        Document newest = loadedFeed.get(0);
        Document oldest = loadedFeed.get(loadedFeed.size() - 1);
        newestFeedMomentId = getMomentId(newest);
        oldestFeedMomentId = getMomentId(oldest);
        oldestFeedTimestamp = readMomentTimestamp(oldest);
    }

    private long readMomentTimestamp(Document moment) {
        if (moment == null) {
            return 0L;
        }
        Object value = moment.get("timestamp");
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private void refreshFeedIfNeeded() {
        if (loadingInitialFeed || loadingMoreFeed) {
            return;
        }
        UserProfile me = Session.getProfile();
        if (me == null || me.username == null || me.username.isBlank()) {
            return;
        }

        Task<FeedRefreshSnapshot> task = new Task<>() {
            @Override
            protected FeedRefreshSnapshot call() {
                List<String> friends = resolveFriendUsernames(me.username);
                List<Document> latest = MomentsService.getFeedPage(me.username, friends, 1, null, null);
                String latestId = latest.isEmpty() ? null : getMomentId(latest.get(0));
                return new FeedRefreshSnapshot(friends, latestId);
            }
        };

        task.setOnSucceeded(ev -> {
            FeedRefreshSnapshot snapshot = task.getValue();
            if (snapshot == null) {
                return;
            }

            boolean audienceChanged = !sameAudience(feedFriendUsernames, snapshot.friendUsernames);
            boolean latestChanged = !Objects.equals(newestFeedMomentId, snapshot.latestMomentId);
            boolean shouldReload = audienceChanged
                    || (snapshot.latestMomentId == null && !loadedFeed.isEmpty())
                    || (snapshot.latestMomentId != null && (loadedFeed.isEmpty() || latestChanged));

            if (shouldReload) {
                loadFeed();
            } else {
                feedFriendUsernames = snapshot.friendUsernames == null ? List.of() : List.copyOf(snapshot.friendUsernames);
            }
        });

        Thread thread = new Thread(task, "moments-feed-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean sameAudience(List<String> left, List<String> right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.size() != right.size()) {
            return false;
        }
        return new java.util.HashSet<>(left).equals(new java.util.HashSet<>(right));
    }

    private void openMomentDetail(String momentId) {
        if (momentId == null || momentsDetailOverlay == null || momentsDetailContent == null) return;
        detailOpen = true;
        currentDetailMomentId = momentId;
        if (refreshTimeline != null) refreshTimeline.pause();

        Task<MomentDetailPayload> task = new Task<>() {
            @Override
            protected MomentDetailPayload call() {
                Document moment = MomentsService.getMomentById(momentId);
                if (moment == null) {
                    return new MomentDetailPayload(null, Map.of());
                }

                Set<String> usernames = new LinkedHashSet<>();
                String owner = moment.getString("username");
                if (owner != null && !owner.isBlank()) {
                    usernames.add(owner);
                }
                List<Document> comments = moment.getList("comments", Document.class);
                if (comments != null) {
                    for (Document comment : comments) {
                        String commenter = comment.getString("username");
                        if (commenter != null && !commenter.isBlank()) {
                            usernames.add(commenter);
                        }
                    }
                }
                Map<String, Document> usersByUsername = UserService.getUsersByUsernames(usernames);
                return new MomentDetailPayload(moment, usersByUsername);
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> {
            MomentDetailPayload payload = task.getValue();
            Document moment = payload == null ? null : payload.moment;
            if (moment == null) return;
            mergeUserDocsIntoCache(payload.usersByUsername);
            momentsDetailContent.getChildren().clear();
            momentsDetailContent.getChildren().add(buildMomentDetail(moment));
            momentsDetailOverlay.setManaged(true);
            momentsDetailOverlay.setVisible(true);
            momentsDetailOverlay.toFront();
        }));
        Thread detailThread = new Thread(task, "moment-detail");
        detailThread.setDaemon(true);
        detailThread.start();
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

        Document userDoc = getUserDocCached(username);
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
        setAvatarImage(avatar, profilePic, 20);

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
        Map<String, Document> commentsById = indexCommentsById(comments);
        Document[] activeReplyTarget = new Document[1];

        Label replyTargetLabel = new Label();
        replyTargetLabel.getStyleClass().add("moment-comment-replying");
        replyTargetLabel.setVisible(false);
        replyTargetLabel.setManaged(false);

        Button clearReplyBtn = new Button("Cancel");
        clearReplyBtn.getStyleClass().add("moment-comment-reply-clear-btn");
        clearReplyBtn.setVisible(false);
        clearReplyBtn.setManaged(false);

        Region replyStateSpacer = new Region();
        HBox.setHgrow(replyStateSpacer, Priority.ALWAYS);
        HBox replyStateRow = new HBox(8, replyTargetLabel, replyStateSpacer, clearReplyBtn);
        replyStateRow.setAlignment(Pos.CENTER_LEFT);
        replyStateRow.getStyleClass().add("moment-comment-reply-state");

        TextField commentField = new TextField();
        commentField.setPromptText("Write a comment...");
        commentField.getStyleClass().add("moment-comment-input");
        HBox.setHgrow(commentField, Priority.ALWAYS);

        Runnable clearReplyTarget = () -> {
            activeReplyTarget[0] = null;
            replyTargetLabel.setVisible(false);
            replyTargetLabel.setManaged(false);
            clearReplyBtn.setVisible(false);
            clearReplyBtn.setManaged(false);
            commentField.setPromptText("Write a comment...");
        };

        Runnable refreshReplyState = () -> {
            Document target = activeReplyTarget[0];
            if (target == null) {
                clearReplyTarget.run();
                return;
            }
            String replyName = resolveUserDisplayName(target.getString("username"));
            String replyPreview = buildReplyPreview(target.getString("text"));
            replyTargetLabel.setText("Replying to " + replyName + ": " + replyPreview);
            replyTargetLabel.setVisible(true);
            replyTargetLabel.setManaged(true);
            clearReplyBtn.setVisible(true);
            clearReplyBtn.setManaged(true);
            commentField.setPromptText("Reply to " + replyName + "...");
        };

        if (comments != null) {
            for (Document c : comments) {
                commentsBox.getChildren().add(buildCommentRow(c, commentsById, () -> {
                    activeReplyTarget[0] = c;
                    refreshReplyState.run();
                    commentField.requestFocus();
                    commentField.positionCaret(commentField.getText() == null ? 0 : commentField.getText().length());
                }));
            }
        }
        card.getChildren().add(commentsBox);
        card.getChildren().add(replyStateRow);

        HBox commentInput = new HBox(8);
        commentInput.setAlignment(Pos.CENTER_LEFT);
        Button postBtn = new Button("Post");
        postBtn.getStyleClass().add("moment-comment-btn");
        commentInput.getChildren().addAll(commentField, postBtn);
        card.getChildren().add(commentInput);

        Runnable postComment = () -> {
            if (me == null) return;
            String cText = commentField.getText() == null ? "" : commentField.getText().trim();
            if (cText.isEmpty()) return;
            if (cText.length() > COMMENT_MAX_TEXT) cText = cText.substring(0, COMMENT_MAX_TEXT);

            Document target = activeReplyTarget[0];
            String parentCommentId = target == null ? null : resolveCommentId(target);
            String replyToUsername = target == null ? null : resolveUserDisplayName(target.getString("username"));
            String replyPreview = target == null ? null : target.getString("text");

            MomentsService.addComment(momentId, me.username, cText, parentCommentId, replyToUsername, replyPreview);
            commentField.clear();
            clearReplyTarget.run();
            openMomentDetail(momentId);
            loadFeed();
        };
        postBtn.setOnAction(e -> postComment.run());
        commentField.setOnAction(e -> postComment.run());
        clearReplyBtn.setOnAction(e -> clearReplyTarget.run());

        return card;
    }

    private VBox buildMomentCard(Document moment) {
        String username = moment.getString("username");
        String text = moment.getString("text");
        String imageUrl = moment.getString("imageUrl");
        long timestamp = moment.containsKey("timestamp") ? moment.getLong("timestamp") : Instant.now().toEpochMilli();
        String momentId = getMomentId(moment);

        Document userDoc = getUserDocCached(username);
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
        setAvatarImage(avatar, profilePic, 19);

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

    private HBox buildCommentRow(Document comment, Map<String, Document> commentsById, Runnable onReply) {
        String username = comment.getString("username");
        String text = comment.getString("text");

        Document userDoc = getUserDocCached(username);
        String displayName = userDoc != null ? userDoc.getString("name") : username;
        String profilePic = userDoc != null ? userDoc.getString("profilePic") : null;

        String parentCommentId = resolveParentCommentId(comment);
        Document parentComment = parentCommentId == null || commentsById == null ? null : commentsById.get(parentCommentId);

        HBox row = new HBox(6);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("moment-comment-row");
        if (parentComment != null) {
            row.getStyleClass().add("moment-comment-row-reply");
            row.setPadding(new Insets(0, 0, 0, COMMENT_REPLY_INDENT));
        }

        Label nameLabel = new Label(displayName);
        nameLabel.getStyleClass().add("moment-comment-name");
        nameLabel.getStyleClass().add("profile-link");
        nameLabel.setOnMouseClicked(e -> openUserProfile(username));

        Label textLabel = new Label(text == null ? "" : text);
        textLabel.setWrapText(true);
        textLabel.getStyleClass().add("moment-comment-text");

        VBox textBox = new VBox(2);
        textBox.setAlignment(Pos.TOP_LEFT);
        textBox.getChildren().add(nameLabel);

        if (parentComment != null) {
            String parentDisplayName = resolveUserDisplayName(parentComment.getString("username"));
            String parentPreview = firstNonBlank(comment.getString("replyPreview"), parentComment.getString("text"));
            Label replyContext = new Label("↳ " + parentDisplayName + ": " + buildReplyPreview(parentPreview));
            replyContext.getStyleClass().add("moment-comment-reply-context");
            textBox.getChildren().add(replyContext);
        }

        textBox.getChildren().add(textLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button replyBtn = new Button("Reply");
        replyBtn.getStyleClass().add("moment-comment-reply-btn");
        replyBtn.setFocusTraversable(false);
        replyBtn.setOnAction(e -> {
            if (onReply != null) {
                onReply.run();
            }
        });

        ImageView avatar = new ImageView();
        avatar.setFitWidth(22);
        avatar.setFitHeight(22);
        avatar.setPreserveRatio(true);
        setAvatarImage(avatar, profilePic, 11);
        avatar.getStyleClass().add("moment-comment-avatar");

        row.getChildren().addAll(textBox, spacer, replyBtn, avatar);
        return row;
    }

    private Map<String, Document> indexCommentsById(List<Document> comments) {
        Map<String, Document> commentsById = new HashMap<>();
        if (comments == null) {
            return commentsById;
        }
        for (Document comment : comments) {
            String id = resolveCommentId(comment);
            if (id != null) {
                commentsById.putIfAbsent(id, comment);
            }
        }
        return commentsById;
    }

    private String resolveCommentId(Document comment) {
        if (comment == null) {
            return null;
        }

        String explicitId = firstNonBlank(
                comment.getString("commentId"),
                comment.getString("id"),
                comment.getString("_id")
        );
        if (explicitId != null) {
            return explicitId;
        }

        return buildLegacyCommentId(comment);
    }

    private String resolveParentCommentId(Document comment) {
        if (comment == null) {
            return null;
        }
        return firstNonBlank(
                comment.getString("parentCommentId"),
                comment.getString("replyToCommentId"),
                comment.getString("replyToId"),
                comment.getString("parentId"),
                comment.getString("replyTo")
        );
    }

    private String buildLegacyCommentId(Document comment) {
        if (comment == null) {
            return null;
        }
        String username = firstNonBlank(comment.getString("username"), "unknown");
        String text = firstNonBlank(comment.getString("text"), "");
        Object timestampObj = comment.get("timestamp");
        String timestamp = timestampObj == null ? "0" : String.valueOf(timestampObj);
        int textHash = text.hashCode();
        return "legacy:" + username + ":" + timestamp + ":" + textHash;
    }

    private String resolveUserDisplayName(String username) {
        if (username == null || username.isBlank()) {
            return "Unknown";
        }
        Document doc = getUserDocCached(username);
        String displayName = doc == null ? null : doc.getString("name");
        return firstNonBlank(displayName, username);
    }

    private String buildReplyPreview(String text) {
        if (text == null || text.isBlank()) {
            return "message";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 72) {
            return normalized;
        }
        return normalized.substring(0, 71) + "…";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
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
                    String imageUrl = ImgBbService.uploadImage(data, 7 * 24 * 60 * 60).imageUrl;
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
            Scene scene = momentsFeed == null ? null : momentsFeed.getScene();
            if (scene == null) return;
            String cssPath = MainApp.currentTheme == MainApp.Theme.DARK ? "/main_dark.css" : "/main.css";
            SceneNavigator.swapRootWithFade(scene, "/view_profile.fxml", cssPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onBackToMain(javafx.event.ActionEvent event) {
        try {
            if (refreshTimeline != null) refreshTimeline.stop();
            Scene scene = momentsFeed == null ? null : momentsFeed.getScene();
            if (scene == null) return;
            String cssPath = MainApp.currentTheme == MainApp.Theme.DARK ? "/main_dark.css" : "/main.css";
            SceneNavigator.swapRootWithFade(scene, "/main.fxml", cssPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
