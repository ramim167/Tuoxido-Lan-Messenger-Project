package com.lanmessenger;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.control.OverrunStyle;
import javafx.beans.binding.Bindings;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import javafx.event.ActionEvent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URI;
import java.awt.Desktop;

import javafx.scene.control.Alert;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ContextMenu;

public class MainController {

    @FXML
    private Label chatTitleLabel;
    @FXML
    private Button audioCallBtn;
    @FXML
    private Button videoCallBtn;
    @FXML
    private Label chatSubtitleLabel;
    @FXML
    private VBox chatHeaderBox;
    @FXML
    private ImageView chatAvatarView;

    @FXML
    private Button blockedItemBtn;

    private String lastInboxState = "";

    @FXML
    private ListView<String> usersList;
    @FXML
    private TextField searchField;
    private long lastMessageTimestamp = 0;

    @FXML
    private ScrollPane messagesScroll;
    @FXML
    private VBox messagesBox;

    @FXML
    private Button attachmentBtn;

    @FXML
    private StackPane imagePreviewOverlay;
    @FXML
    private StackPane imagePreviewPane;
    @FXML
    private ImageView imagePreviewView;

    @FXML
    private TextField messageField;
    @FXML
    private VBox composerContainer;
    @FXML
    private Button emojiBtn;
    @FXML
    private Button sendBtn;

    @FXML
    private BorderPane rootPane;
    @FXML
    private HBox appRoot;
    @FXML
    private StackPane logoutOverlay;

    @FXML
    private Button chatBtn;
    @FXML
    private Button settingsBtn;
    @FXML
    private Button profileBtn;
    @FXML
    private Button archiveBtn;

    @FXML
    private VBox sidebar;

    @FXML
    private VBox settingsDrawer;

    @FXML
    private Label drawerTitleLabel;
    @FXML
    private VBox drawerContent;

    @FXML
    private Button profileItemBtn;
    @FXML
    private Button activeItemBtn;
    @FXML
    private Button themeItemBtn;
    @FXML
    private Button logoutItemBtn;

    @FXML
    private VBox activeSection;
    @FXML
    private VBox themeSection;

    @FXML
    private ToggleButton activeToggle;
    @FXML
    private Region activeThumb;

    @FXML
    private RadioButton lightRadio;
    @FXML
    private RadioButton darkRadio;

    @FXML
    private FontIcon activeChevron;
    @FXML
    private FontIcon themeChevron;

    @FXML
    private VBox profileSection;
    @FXML
    private FontIcon profileChevron;

    @FXML
    private TextField profileNameField;
    @FXML
    private Label profileEmailValue;
    @FXML
    private TextField profileUsernameField;
    @FXML
    private DatePicker profileBirthdatePicker;
    @FXML
    private Label profileStatusLabel;
    @FXML
    private Button btnFriends;
    @FXML
    private Button btnMoments;

    private ToggleGroup themeGroup;

    private static final double COLLAPSED_W = 72;
    private static final double EXPANDED_W = 240;

    private Thread chatListenerThread;
    private Thread incomingCallThread;
    private Thread globalInboxThread;
    private volatile boolean keepListening = false;
    private int displayedMessageCount = 0;
    private static final double DRAWER_W = 240;

    private static final double PILL_COLLAPSED_W = 44;
    private static final double PILL_EXPANDED_W = 212;

    private static final Duration ANIM_DUR = Duration.millis(200);
    private static final Duration DRAWER_ANIM_DUR = Duration.millis(220);
    private static final Duration SECTION_ANIM_DUR = Duration.millis(180);
    private static final Duration SWITCH_ANIM_DUR = Duration.millis(140);
    private boolean showingArchived = false;

    private static final Duration COLLAPSE_DELAY = Duration.millis(120);
    private PauseTransition collapseDelay;

    private boolean expanded = false;
    private boolean sidebarBusy = false;
    private boolean themeSwitching = false;
    private boolean drawerOpen = false;
    private boolean mouseOverSidebar = false;

    private String currentChatUsername = null;
    private Timeline sidebarAnim;
    private Timeline drawerAnim;
    private Timeline sectionAnim;

    private enum DrawerMode {SETTINGS, PROFILE}

    private DrawerMode drawerMode = DrawerMode.SETTINGS;

    private enum Sender {ME, OTHER}

    private Sender lastSender = null;
    private HBox lastBubbleRow = null;

    private final ObservableList<String> allInboxes = FXCollections.observableArrayList();
    private FilteredList<String> filteredInboxes;

    private String editingMsgId = null;
    private String replyingName = null;
    private String replyingText = null;
    private VBox actionIndicatorBox;
    private Label actionTitle;
    private Label actionSubtitle;
    private volatile boolean isAppActive = true;
    private static volatile boolean isCallAlertShowing = false;
    // Reopen the selected chat after returning from a secondary screen.
    public static String pendingOpenChatUsername = null;
    private java.util.List<Document> lastHistory = new ArrayList<>();
    private static final java.util.Map<String, Image> IMAGE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Image> USER_AVATAR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile List<String> cachedActiveInboxes = List.of();
    private static volatile List<String> cachedArchivedInboxes = List.of();
    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024;
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+|www\\.\\S+)");
    private boolean isChatBlocked = false;
    private boolean chatBlockedByMe = false;
    private long lastBlockedCheckAt = 0L;

    @FXML
    public void initialize() {
        isAppActive = true;
        setActive(chatBtn, chatBtn, archiveBtn, profileBtn, settingsBtn);
        SmoothScrollUtil.apply(messagesScroll);

        bindControllerLifecycle();
        initSidebarDefaults();
        initSettingsDrawer();
        initProfilePanel();
        initDemoInbox();
        initComposer();
        initActionIndicator();
        initModal();
        initImagePreview();
        if (chatAvatarView != null) {
            double radius = Math.max(chatAvatarView.getFitWidth(), chatAvatarView.getFitHeight()) / 2.0;
            chatAvatarView.setClip(new javafx.scene.shape.Circle(radius, radius, radius));
            chatAvatarView.setVisible(false);
            chatAvatarView.setManaged(false);
        }
        showEmptyChatState(false);
        applyCachedRecentChats();
        loadRecentChats();
        startIncomingCallListener();
        startGlobalInboxListener();

        if (messagesBox != null && messagesScroll != null) {
            messagesBox.heightProperty().addListener((observable, oldValue, newValue) -> {
                messagesScroll.setVvalue(1.0);
            });
        }
    }

    private void initActionIndicator() {
        actionIndicatorBox = new VBox(3);
        actionIndicatorBox.getStyleClass().add("action-indicator-box");
        actionIndicatorBox.setManaged(false);
        actionIndicatorBox.setVisible(false);

        HBox topRow = new HBox();
        topRow.getStyleClass().add("action-indicator-top");
        actionTitle = new Label();
        actionTitle.getStyleClass().add("action-indicator-title");
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("action-indicator-close");
        closeBtn.setOnAction(e -> cancelAction());
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        topRow.getChildren().addAll(actionTitle, sp, closeBtn);

        actionSubtitle = new Label();
        actionSubtitle.getStyleClass().add("action-indicator-subtitle");
        actionIndicatorBox.getChildren().addAll(topRow, actionSubtitle);

        Platform.runLater(() -> {
            try {
                if (composerContainer != null && !composerContainer.getChildren().contains(actionIndicatorBox)) {
                    composerContainer.getChildren().add(0, actionIndicatorBox);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void bindControllerLifecycle() {
        if (rootPane == null) {
            return;
        }
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                shutdownBackgroundWork();
            }
        });
    }

    private void shutdownBackgroundWork() {
        isAppActive = false;
        stopChatListener();

        if (incomingCallThread != null) {
            incomingCallThread.interrupt();
        }
        if (globalInboxThread != null) {
            globalInboxThread.interrupt();
        }
    }

    private void applyCachedRecentChats() {
        List<String> cached = showingArchived ? cachedArchivedInboxes : cachedActiveInboxes;
        if (cached != null && !cached.isEmpty()) {
            applyRecentChatsToUi(new ArrayList<>(cached));
        }
    }

    private void updateRecentChatsCache(List<String> partners) {
        List<String> snapshot = List.copyOf(partners);
        if (showingArchived) {
            cachedArchivedInboxes = snapshot;
        } else {
            cachedActiveInboxes = snapshot;
        }
    }

    private void openGroupSettings(String groupId) {
        if (groupId == null || groupId.isBlank() || chatTitleLabel == null || chatTitleLabel.getScene() == null) {
            return;
        }

        try {
            GroupSettingsController.targetGroupId = groupId;

            Stage stage = (Stage) chatTitleLabel.getScene().getWindow();
            Scene scene = stage.getScene();
            Parent root = new FXMLLoader(MainApp.class.getResource("/group_settings.fxml")).load();
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

    private void cancelAction() {
        editingMsgId = null;
        replyingName = null;
        replyingText = null;
        if (actionIndicatorBox != null) {
            actionIndicatorBox.setVisible(false);
            actionIndicatorBox.setManaged(false);
        }
        if (messageField != null) {
            messageField.clear();
        }
    }

    private String truncateText(String text) {
        if (text == null) return "";
        String[] words = text.split("\\s+");
        if (words.length > 9) {
            return String.join(" ", java.util.Arrays.copyOfRange(words, 0, 9)) + "...";
        }
        return text;
    }

    private void startIncomingCallListener() {
        incomingCallThread = new Thread(() -> {
            while (isAppActive) {
                try {
                    Thread.sleep(1500);
                    UserProfile me = Session.getProfile();
                    if (me != null) {
                        Document incomingCall = MessageService.checkIncomingCall(me.username);
                        if (incomingCall != null) {
                            String caller = incomingCall.getString("caller");
                            String callType = incomingCall.getString("type");
                            String status = incomingCall.getString("status");
                            String callerIp = incomingCall.getString("ip");

                            if ("ringing".equals(status) && !isCallAlertShowing) {
                                isCallAlertShowing = true;
                                Platform.runLater(() -> openCallWindow(caller, callType, false, callerIp));
                            }
                        } else {
                            isCallAlertShowing = false;
                        }
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }, "incoming-call-listener");
        incomingCallThread.setDaemon(true);
        incomingCallThread.start();
    }

    private void openCallWindow(String partnerUsername, String callType, boolean isCaller, String callerIp) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/call_window.fxml"));
            Parent root = loader.load();

            CallController controller = loader.getController();
            controller.setupCall(partnerUsername, callType, isCaller, callerIp);

            Stage callStage = new Stage();
            callStage.setTitle("Call with " + partnerUsername);
            Scene scene = new Scene(root);
            ThemeManager.applyMainTheme(scene);
            callStage.setScene(scene);
            callStage.setOnCloseRequest(e -> controller.onEndCall(null));
            callStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startGlobalInboxListener() {
        globalInboxThread = new Thread(() -> {
            while (isAppActive) {
                try {

                    Thread.sleep(800);
                    UserProfile me = Session.getProfile();
                    if (me != null) {
                        List<String> latest = MessageService.getRecentChatPartners(me.username);

                        String currentState = String.join("|", latest);

                        if (!currentState.equals(lastInboxState)) {
                            lastInboxState = currentState;
                            Platform.runLater(() -> {

                                if (searchField.getText() == null || searchField.getText().trim().isEmpty()) {
                                    loadRecentChats(latest);
                                }
                            });
                        }
                    }
                } catch (Exception e) { break; }
            }
        }, "global-inbox-listener");
        globalInboxThread.setDaemon(true);
        globalInboxThread.start();
    }

    private void initSidebarDefaults() {

        if (sidebar != null) {
            if (!sidebar.getStyleClass().contains("collapsed")) sidebar.getStyleClass().add("collapsed");
            sidebar.getStyleClass().remove("expanded");
            sidebar.setPrefWidth(COLLAPSED_W);
            sidebar.setPickOnBounds(true);
        }

        if (chatBtn != null) chatBtn.setMaxWidth(Double.MAX_VALUE);
        if (archiveBtn != null) archiveBtn.setMaxWidth(Double.MAX_VALUE);
        if (settingsBtn != null) settingsBtn.setMaxWidth(Double.MAX_VALUE);

        collapseDelay = new PauseTransition(COLLAPSE_DELAY);
        collapseDelay.setOnFinished(e -> {
            if (!drawerOpen && !mouseOverSidebar && !themeSwitching && !sidebarBusy) {
                collapseSidebar();
            }
        });

        Platform.runLater(() -> {
            if (sidebar == null) return;
            sidebar.applyCss();
            sidebar.layout();

            setNavTextVisible(false);
            setNavPillWidth(PILL_COLLAPSED_W);

            expanded = false;
        });
    }

    private void initComposer() {
        if (messageField == null) return;
        initEmojiPicker();

        messageField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                onSend();
                e.consume();
            }
        });
    }

    private void initEmojiPicker() {
        if (emojiBtn == null || messageField == null) return;

        ContextMenu emojiMenu = new ContextMenu();
        emojiMenu.setAutoHide(true);

        String[][] emojiRows = {
                {"😀", "😄", "😊", "🙂", "😉", "😍", "🥰", "😘"},
                {"😂", "🤣", "😎", "🔥", "✨", "🎉", "👍", "❤️"},
                {"🙏", "👏", "👌", "💯", "🤝", "🤔", "😢", "😭"}
        };

        javafx.scene.layout.VBox menuBox = new javafx.scene.layout.VBox(8);
        menuBox.setStyle(
                "-fx-padding: 10;" +
                "-fx-background-color: -lm-panel;" +
                "-fx-background-radius: 14;" +
                "-fx-border-color: -lm-border;" +
                "-fx-border-radius: 14;"
        );

        for (String[] row : emojiRows) {
            javafx.scene.layout.HBox rowBox = new javafx.scene.layout.HBox(6);
            for (String emoji : row) {
                Button emojiItem = new Button(emoji);
                emojiItem.setFocusTraversable(false);
                emojiItem.setStyle(
                        "-fx-background-color: transparent;" +
                        "-fx-font-size: 18px;" +
                        "-fx-background-radius: 10;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 6 8 6 8;"
                );
                emojiItem.setOnAction(e -> {
                    insertEmoji(emoji);
                    emojiMenu.hide();
                });
                rowBox.getChildren().add(emojiItem);
            }
            menuBox.getChildren().add(rowBox);
        }

        CustomMenuItem contentItem = new CustomMenuItem(menuBox, false);
        contentItem.setHideOnClick(false);
        emojiMenu.getItems().setAll(contentItem);
        emojiBtn.getProperties().put("emojiMenu", emojiMenu);
    }

    @FXML
    private void onOpenEmojiPicker() {
        if (emojiBtn == null) return;
        Object stored = emojiBtn.getProperties().get("emojiMenu");
        if (!(stored instanceof ContextMenu emojiMenu)) return;

        if (emojiMenu.isShowing()) {
            emojiMenu.hide();
        } else {
            emojiMenu.show(emojiBtn, javafx.geometry.Side.TOP, 0, 8);
        }
    }

    private void insertEmoji(String emoji) {
        if (messageField == null || emoji == null || emoji.isEmpty()) return;

        int caret = Math.max(0, messageField.getCaretPosition());
        String currentText = messageField.getText() == null ? "" : messageField.getText();
        messageField.setText(currentText.substring(0, caret) + emoji + currentText.substring(caret));
        messageField.positionCaret(caret + emoji.length());
        messageField.requestFocus();
    }

    private void initModal() {
        if (logoutOverlay != null) {
            logoutOverlay.setVisible(false);
            logoutOverlay.setManaged(false);
        }
    }

    private void initImagePreview() {
        if (imagePreviewOverlay != null) {
            imagePreviewOverlay.setVisible(false);
            imagePreviewOverlay.setManaged(false);
        }
        if (imagePreviewView != null && imagePreviewPane != null) {
            imagePreviewView.fitWidthProperty().bind(Bindings.subtract(imagePreviewPane.widthProperty(), 28));
            imagePreviewView.fitHeightProperty().bind(Bindings.subtract(imagePreviewPane.heightProperty(), 28));
        }
    }

    private void showImagePreview(Image image) {
        if (imagePreviewOverlay == null || imagePreviewView == null || image == null) return;
        imagePreviewView.setImage(image);
        imagePreviewOverlay.setManaged(true);
        imagePreviewOverlay.setVisible(true);
        imagePreviewOverlay.toFront();
    }

    @FXML
    private void onCloseImagePreview() {
        if (imagePreviewOverlay == null) return;
        imagePreviewOverlay.setVisible(false);
        imagePreviewOverlay.setManaged(false);
        if (imagePreviewView != null) {
            imagePreviewView.setImage(null);
        }
    }

    private Image getCachedImage(String url) {
        if (url == null) return null;
        String key = url.trim();
        if (key.isEmpty()) return null;
        return IMAGE_CACHE.computeIfAbsent(key, u -> new Image(u, true));
    }

    private Image getFallbackAvatarImage() {
        try (var in = MainApp.class.getResourceAsStream("/assets/logo_alt.png")) {
            return in == null ? null : new Image(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showChatHeaderAvatar(Image image) {
        if (chatAvatarView == null) return;
        chatAvatarView.setImage(image);
        chatAvatarView.setManaged(image != null);
        chatAvatarView.setVisible(image != null);
    }

    private void hideChatHeaderAvatar() {
        if (chatAvatarView == null) return;
        chatAvatarView.setImage(null);
        chatAvatarView.setManaged(false);
        chatAvatarView.setVisible(false);
    }

    private void loadUserAvatarInto(ImageView target, String username, String expectedChatId) {
        if (target == null || username == null || username.isBlank()) return;

        Image fallback = getFallbackAvatarImage();
        if (fallback != null) {
            target.setImage(fallback);
        }

        Image cached = USER_AVATAR_CACHE.get(username);
        if (cached != null) {
            target.setImage(cached);
            return;
        }

        Thread t = new Thread(() -> {
            Image resolved = fallback;
            try {
                Document uDoc = UserService.getUserByUsername(username);
                if (uDoc != null) {
                    String pic = uDoc.getString("profilePic");
                    if (pic != null && pic.startsWith("http")) {
                        Image fromUrl = getCachedImage(pic);
                        if (fromUrl != null) {
                            resolved = fromUrl;
                            USER_AVATAR_CACHE.put(username, fromUrl);
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            Image finalResolved = resolved;
            Platform.runLater(() -> {
                if (expectedChatId != null && !expectedChatId.equals(currentChatUsername)) {
                    return;
                }
                target.setImage(finalResolved);
            });
        }, "load-user-avatar-" + username);
        t.setDaemon(true);
        t.start();
    }

    private void loadGroupAvatarInto(ImageView target, String groupId) {
        if (target == null || groupId == null || groupId.isBlank()) return;
        Image fallback = getFallbackAvatarImage();
        if (fallback != null) {
            target.setImage(fallback);
        }

        Thread t = new Thread(() -> {
            Image resolved = fallback;
            try {
                Document group = GroupService.getGroupById(groupId);
                if (group != null) {
                    String pic = group.getString("groupPic");
                    if (pic != null && pic.startsWith("http")) {
                        Image fromUrl = getCachedImage(pic);
                        if (fromUrl != null) {
                            resolved = fromUrl;
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            Image finalResolved = resolved;
            Platform.runLater(() -> {
                if (!groupId.equals(currentChatUsername)) {
                    return;
                }
                target.setImage(finalResolved);
            });
        }, "load-group-avatar-" + groupId);
        t.setDaemon(true);
        t.start();
    }

    private void updateChatHeaderAvatar(String chatIdOrUsername, boolean groupChat) {
        if (chatAvatarView == null || chatIdOrUsername == null || chatIdOrUsername.isBlank()) {
            hideChatHeaderAvatar();
            return;
        }

        showChatHeaderAvatar(getFallbackAvatarImage());
        if (groupChat) {
            loadGroupAvatarInto(chatAvatarView, chatIdOrUsername);
        } else {
            loadUserAvatarInto(chatAvatarView, chatIdOrUsername, chatIdOrUsername);
        }
    }

    private void initDemoInbox() {
        if (usersList == null) return;

        allInboxes.clear();
        filteredInboxes = new FilteredList<>(allInboxes, s -> true);
        usersList.setItems(filteredInboxes);

        usersList.setCellFactory(lv -> new ListCell<String>() {
            private HBox row;
            private Label name;
            private Label preview;
            private Label time;
            private Button menuBtn;
            private ContextMenu contextMenu;
            private VBox v;
            private Region spacer;

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null); return;
                }

                if (row == null) {
                    name = new Label(); name.getStyleClass().add("inbox-name");
                    preview = new Label(); preview.getStyleClass().add("inbox-preview");
                    v = new VBox(name, preview); v.setSpacing(2);
                    spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                    time = new Label(); time.getStyleClass().add("inbox-time");

                    menuBtn = new Button("⋮");
                    menuBtn.setStyle("-fx-background-color: transparent; -fx-font-size: 18px; -fx-font-weight: bold; -fx-cursor: hand; -fx-text-fill: -lm-muted; -fx-padding: 0 5 0 10;");
                    contextMenu = new ContextMenu();
                    MenuItem archiveItem = new MenuItem("Archive");
                    MenuItem deleteItem = new MenuItem("Delete");
                    MenuItem profileItem = new MenuItem("View Profile");

                    archiveItem.setOnAction(e -> {
                        String curItem = getItem(); if (curItem == null) return;
                        String dPart = curItem.split(":::")[0];
                        String partnerUser = dPart.contains(" (@") ? dPart.substring(dPart.lastIndexOf(" (@") + 3, dPart.length() - 1) : dPart;
                        UserProfile me = Session.getProfile();
                        if (me != null) { UserService.toggleArchive(me.username, partnerUser, !showingArchived); loadRecentChats(); }
                    });

                    deleteItem.setOnAction(e -> {
                        String curItem = getItem(); if (curItem == null) return;
                        String dPart = curItem.split(":::")[0];
                        String partnerUser = dPart.contains(" (@") ? dPart.substring(dPart.lastIndexOf(" (@") + 3, dPart.length() - 1) : dPart;
                        UserProfile me = Session.getProfile();
                        if (me != null) {
                            MessageService.deleteChatHistory(me.username, partnerUser);
                            if (partnerUser.equals(currentChatUsername)) {
                                currentChatUsername = null;
                                if (chatTitleLabel != null) chatTitleLabel.setText("Chat Deleted");
                                if (chatSubtitleLabel != null) chatSubtitleLabel.setText("");
                                if (messagesBox != null) messagesBox.getChildren().clear();
                            }
                            loadRecentChats();
                        }
                    });

                    profileItem.setOnAction(e -> {
                        String curItem = getItem(); if (curItem == null) return;
                        String dPart = curItem.split(":::")[0];
                        String partnerUser = dPart.contains(" (@") ? dPart.substring(dPart.lastIndexOf(" (@") + 3, dPart.length() - 1) : dPart;
                        if (isGroupChatSelection(partnerUser)) return;
                        ViewProfileController.targetUsername = partnerUser;
                        ViewProfileController.returnTarget = ViewProfileController.ReturnTarget.MAIN;
                        try {
                            Stage stage = (Stage) menuBtn.getScene().getWindow();
                            Scene scene = stage.getScene();
                            Parent root = new FXMLLoader(MainApp.class.getResource("/view_profile.fxml")).load();
                            root.setOpacity(0);
                            FadeTransition out = new FadeTransition(Duration.millis(200), scene.getRoot());
                            out.setFromValue(1); out.setToValue(0);
                            out.setOnFinished(ev -> {
                                scene.setRoot(root); ThemeManager.applyMainTheme(scene);
                                FadeTransition in = new FadeTransition(Duration.millis(250), root);
                                in.setFromValue(0); in.setToValue(1); in.play();
                            });
                            out.play();
                        } catch (Exception ex) { ex.printStackTrace(); }
                    });

                    contextMenu.getItems().addAll(archiveItem, deleteItem);
                    contextMenu.getStyleClass().add("custom-menu");

                    menuBtn.setOnMouseClicked(e -> {
                        archiveItem.setText(showingArchived ? "Unarchive" : "Archive");
                        String curItem = getItem();
                        if (curItem != null) {
                            String dPart = curItem.split(":::")[0];
                            String partnerUser = dPart.contains(" (@") ? dPart.substring(dPart.lastIndexOf(" (@") + 3, dPart.length() - 1) : dPart;
                            boolean isGroup = isGroupChatSelection(partnerUser);
                            contextMenu.getItems().setAll(archiveItem, deleteItem);
                            if (!isGroup) {
                                contextMenu.getItems().add(profileItem);
                            }
                        }
                        contextMenu.show(menuBtn, e.getScreenX(), e.getScreenY());
                        if (contextMenu.getScene() != null && menuBtn.getScene() != null) {
                            contextMenu.getScene().getStylesheets().setAll(menuBtn.getScene().getStylesheets());
                            if (!contextMenu.getScene().getRoot().getStyleClass().contains("root")) {
                                contextMenu.getScene().getRoot().getStyleClass().add("root");
                            }
                        }
                        e.consume();
                    });

                    row = new HBox(v, spacer, time, menuBtn);
                    row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(10, 12, 10, 12));
                    row.getStyleClass().add("inbox-row");
                }

                String displayPart = item;
                long timestamp = System.currentTimeMillis();
                String previewText = "New conversation";
                int unreadCountNum = 0;

                if (item.contains(":::")) {
                    String[] parts = item.split(":::");
                    displayPart = parts[0];
                    if (parts.length > 1) try { timestamp = Long.parseLong(parts[1]); } catch(Exception ignored){}
                    if (parts.length > 2) previewText = parts[2];
                    if (parts.length > 3) try { unreadCountNum = Integer.parseInt(parts[3]); } catch(Exception ignored){}
                }

                String displayName = displayPart;
                if (displayPart.contains(" (@") && displayPart.endsWith(")")) {
                    int atIndex = displayPart.lastIndexOf(" (@");
                    displayName = displayPart.substring(0, atIndex);
                }

                name.setText(displayName);
                preview.setText(previewText);

                long diffMsg = System.currentTimeMillis() - timestamp;
                long minutes = diffMsg / 60000;
                if (minutes < 1) time.setText("Now");
                else if (minutes < 60) time.setText(minutes + "m");
                else if (minutes < 1440) time.setText((minutes / 60) + "h");
                else time.setText((minutes / 1440) + "d");

                javafx.scene.shape.Circle blueDot = new javafx.scene.shape.Circle(4, javafx.scene.paint.Color.web("#0084FF"));
                Label badgeLabel = new Label(String.valueOf(unreadCountNum));
                badgeLabel.setStyle("-fx-background-color: #0084FF; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 1 5; -fx-background-radius: 10;");

                HBox rightBox = new HBox(8);
                rightBox.setAlignment(Pos.CENTER_RIGHT);

                if (unreadCountNum > 0 && !displayPart.contains("(@" + currentChatUsername + ")")) {
                    name.setStyle("-fx-font-weight: 900; -fx-text-fill: -lm-text;");
                    preview.setStyle("-fx-font-weight: bold; -fx-text-fill: -lm-text;");
                    rightBox.getChildren().addAll(blueDot, badgeLabel, time, menuBtn);
                } else {
                    name.setStyle("-fx-font-weight: bold; -fx-text-fill: -lm-text;");
                    preview.setStyle("-fx-font-weight: normal; -fx-text-fill: -lm-muted;");
                    rightBox.getChildren().addAll(time, menuBtn);
                }

                row.getChildren().clear();
                row.getChildren().addAll(v, spacer, rightBox);

                setGraphic(row); setText(null);
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                if (row != null) {
                    row.getStyleClass().remove("inbox-selected");
                    if (selected) row.getStyleClass().add("inbox-selected");
                }
            }
        });

        usersList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) selectInbox(newV);
        });

        if (searchField != null) {
            searchField.textProperty().addListener((o, old, text) -> {
                final String q = (text == null) ? "" : text.trim();
                if (q.isEmpty()) { loadRecentChats(); return; }
                Task<List<Document>> searchTask = new Task<>() {
                    @Override protected List<Document> call() { return UserService.searchUsers(q); }
                };
                searchTask.setOnSucceeded(ev -> {
                    List<Document> results = searchTask.getValue();
                    List<String> displayList = new ArrayList<>();
                    UserProfile me = Session.getProfile();
                    for (Document doc : results) {
                        String dbUsername = doc.getString("username");
                        if (me != null && dbUsername.equals(me.username)) continue;
                        String dbName = doc.getString("name");

                        displayList.add(dbName + " (@" + dbUsername + "):::" + System.currentTimeMillis() + ":::Tap to chat");
                    }
                    Platform.runLater(() -> {
                        allInboxes.setAll(displayList);
                        filteredInboxes.setPredicate(s -> true);
                    });
                });
                new Thread(searchTask, "db-search").start();
            });
        }
    }

    private void startChatListener(String me, String partner) {
        stopChatListener();
        keepListening = true;
        displayedMessageCount = 0;

        chatListenerThread = new Thread(() -> {
            while (keepListening) {
                try {
                    List<Document> history = MessageService.getChatHistory(me, partner);

                    boolean changed = false;
                    if (history.size() != lastHistory.size()) {
                        changed = true;
                    } else {
                        for (int i = 0; i < history.size(); i++) {
                            if (!history.get(i).getString("text").equals(lastHistory.get(i).getString("text"))) {
                                changed = true;
                                break;
                            }
                        }
                    }

                    if (changed) {
                        lastHistory = new ArrayList<>(history);
                        MessageService.markMessagesAsRead(me, partner);
                        Platform.runLater(() -> {
                            messagesBox.getChildren().clear();
                            lastMessageTimestamp = 0;
                            lastSender = null;
                            lastBubbleRow = null;

                            for (Document doc : history) {
                                if (doc.getString("sender").equals(me)) addBubble(Sender.ME, doc);
                                else addBubble(Sender.OTHER, doc);
                            }
                            scrollToBottom();

                            if (searchField.getText() == null || searchField.getText().trim().isEmpty()) {
                                loadRecentChats();
                            }
                        });
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastBlockedCheckAt > 4000) {
                        lastBlockedCheckAt = now;
                        boolean[] blockState = resolveBlockState(me, partner);
                        boolean blocked = blockState[0];
                        boolean blockedByMe = blockState[1];
                        if (blocked != isChatBlocked || blockedByMe != chatBlockedByMe) {
                            Platform.runLater(() -> applyBlockedState(blocked, blockedByMe));
                        }
                    }
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "chat-listener");
        chatListenerThread.setDaemon(true);
        chatListenerThread.start();
    }

    private void stopChatListener() {
        keepListening = false;
        if (chatListenerThread != null) {
            chatListenerThread.interrupt();
        }
    }

    private void selectInbox(String item) {
        if (item == null || item.isEmpty()) return;

        String displayPart = item.split(":::")[0];
        String displayName = displayPart;
        String username = "";

        if (displayPart.contains(" (@") && displayPart.endsWith(")")) {
            int atIndex = displayPart.lastIndexOf(" (@");
            displayName = displayPart.substring(0, atIndex);
            username = displayPart.substring(atIndex + 3, displayPart.length() - 1);
        } else {
            username = displayPart;
        }

        if (username.equals(currentChatUsername)) return;
        currentChatUsername = username;
        boolean groupChat = isGroupChatSelection(username);

        if (chatTitleLabel != null) {
            chatTitleLabel.setText(groupChat && displayName.isBlank() ? " " : displayName);
            chatTitleLabel.setManaged(true);
            chatTitleLabel.setVisible(true);
            chatTitleLabel.getStyleClass().remove("profile-link");
            chatTitleLabel.setOnMouseClicked(null);
        }
        if (chatSubtitleLabel != null) {
            chatSubtitleLabel.setText(groupChat ? "" : "@" + username);
            chatSubtitleLabel.setStyle("");
            chatSubtitleLabel.setManaged(!groupChat);
            chatSubtitleLabel.setVisible(!groupChat);
        }
        if (chatHeaderBox != null) {
            chatHeaderBox.setAlignment(Pos.CENTER_LEFT);
        }
        updateChatHeaderAvatar(username, groupChat);
        if (groupChat && chatTitleLabel != null) {
            String selectedGroupId = username;
            if (!chatTitleLabel.getStyleClass().contains("profile-link")) {
                chatTitleLabel.getStyleClass().add("profile-link");
            }
            chatTitleLabel.setOnMouseClicked(e -> openGroupSettings(selectedGroupId));
        }
        if (audioCallBtn != null) {
            audioCallBtn.setDisable(groupChat);
            audioCallBtn.setManaged(!groupChat);
            audioCallBtn.setVisible(!groupChat);
        }
        if (videoCallBtn != null) {
            videoCallBtn.setDisable(groupChat);
            videoCallBtn.setManaged(!groupChat);
            videoCallBtn.setVisible(!groupChat);
        }

        if (messagesBox != null) {
            messagesBox.getChildren().clear();
            lastSender = null; lastBubbleRow = null;
        }

        UserProfile me = Session.getProfile();
        if (me != null && currentChatUsername != null) {

            Thread markReadThread = new Thread(() -> {
                MessageService.markMessagesAsRead(me.username, currentChatUsername);
                Platform.runLater(this::loadRecentChats);
            }, "mark-chat-read");
            markReadThread.setDaemon(true);
            markReadThread.start();

            startChatListener(me.username, currentChatUsername);
        }
        updateBlockedState();

        Platform.runLater(this::scrollToBottom);

        if (usersList != null) {
            int idx = usersList.getItems().indexOf(item);
            if (idx >= 0 && usersList.getSelectionModel().getSelectedIndex() != idx) {
                usersList.getSelectionModel().select(idx);
            }
        }
    }

    private boolean isGroupChatSelection(String username) {
        return username != null && GroupService.getGroupById(username) != null;
    }

    private void scrollToBottom() {
        if (messagesScroll == null || messagesBox == null) return;

        Platform.runLater(() -> {
            messagesBox.layout();
            messagesScroll.layout();
            messagesScroll.setVvalue(1.0);
        });
    }

    private void showEmptyChatState(boolean archivedMode) {
        currentChatUsername = null;
        if (chatTitleLabel != null) {
            chatTitleLabel.setText("");
            chatTitleLabel.setManaged(false);
            chatTitleLabel.setVisible(false);
        }
        if (chatSubtitleLabel != null) {
            chatSubtitleLabel.setText(archivedMode ? "Your archived conversations will appear here" : "Choose a conversation or search a user to begin");
            chatSubtitleLabel.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 11.5px; -fx-opacity: 0.72; -fx-font-weight: 600;");
            chatSubtitleLabel.setManaged(true);
            chatSubtitleLabel.setVisible(true);
        }
        if (chatHeaderBox != null) {
            chatHeaderBox.setAlignment(archivedMode ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        }
        if (audioCallBtn != null) {
            audioCallBtn.setDisable(true);
        }
        if (videoCallBtn != null) {
            videoCallBtn.setDisable(true);
        }
        if (messagesBox != null) {
            messagesBox.getChildren().clear();
        }
        hideChatHeaderAvatar();
        applyBlockedState(false, false);
    }

    private void updateBlockedState() {
        UserProfile me = Session.getProfile();
        if (me == null || currentChatUsername == null) {
            applyBlockedState(false, false);
            return;
        }
        boolean[] blockState = resolveBlockState(me.username, currentChatUsername);
        applyBlockedState(blockState[0], blockState[1]);
    }

    private boolean[] resolveBlockState(String myUsername, String otherUsername) {
        if (myUsername == null || otherUsername == null || myUsername.isBlank() || otherUsername.isBlank()) {
            return new boolean[] { false, false };
        }
        boolean blockedByMe = UserService.isBlockedBy(myUsername, otherUsername);
        boolean blockedByOther = UserService.isBlockedBy(otherUsername, myUsername);
        return new boolean[] { blockedByMe || blockedByOther, blockedByMe };
    }

    private void applyBlockedState(boolean blocked, boolean blockedByMe) {
        isChatBlocked = blocked;
        chatBlockedByMe = blockedByMe;
        if (messageField != null) {
            messageField.setDisable(blocked);
            if (blockedByMe) {
                messageField.setPromptText("You have blocked this account");
            } else if (blocked) {
                messageField.setPromptText("You can't message this account");
            } else {
                messageField.setPromptText("Aa");
            }
            if (blocked) {
                messageField.clear();
                cancelAction();
            }
        }
        if (attachmentBtn != null) attachmentBtn.setDisable(blocked);
        if (emojiBtn != null) emojiBtn.setDisable(blocked);
        if (sendBtn != null) sendBtn.setDisable(blocked);
        boolean disableCalls = blocked || currentChatUsername == null || isGroupChatSelection(currentChatUsername);
        if (audioCallBtn != null) audioCallBtn.setDisable(disableCalls);
        if (videoCallBtn != null) videoCallBtn.setDisable(disableCalls);
    }

    private void addBubble(Sender sender, Document doc) {
        if (messagesBox == null) return;
        long currentMsgTime = doc.getLong("timestamp");

        if (lastMessageTimestamp != 0 && (currentMsgTime - lastMessageTimestamp) > (2 * 60 * 60 * 1000)) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, h:mm a");
            Label timeDivider = new Label(sdf.format(new java.util.Date(currentMsgTime)));
            timeDivider.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 15 0 5 0;");

            HBox timeRow = new HBox(timeDivider);
            timeRow.setAlignment(Pos.CENTER);
            messagesBox.getChildren().add(timeRow);
        }
        lastMessageTimestamp = currentMsgTime;

        boolean isSystemMsg = doc.containsKey("isSystemMsg") && doc.getBoolean("isSystemMsg");
        if (isSystemMsg) {
            String viewerUsername = Session.getProfile() == null ? null : Session.getProfile().username;
            String msgTxt = MessageService.formatSystemMessage(doc, viewerUsername);

            Label sysLbl = new Label(msgTxt);
            sysLbl.setStyle("-fx-text-fill: -lm-muted; -fx-font-size: 11.5px; -fx-font-style: italic; -fx-font-weight: bold;");
            HBox row = new HBox(sysLbl);
            row.setAlignment(Pos.CENTER);
            row.setPadding(new Insets(10, 0, 10, 0));
            messagesBox.getChildren().add(row);
            return;
        }

        String text = doc.getString("text");
        String msgId = doc.getObjectId("_id").toString();
        String replyName = doc.getString("replyName");
        String replyText = doc.getString("replyText");
        Boolean isEdited = doc.getBoolean("isEdited");
        boolean isUnsent = Boolean.TRUE.equals(doc.getBoolean("isUnsent"));

        boolean sameChain = (lastSender == sender) && lastBubbleRow != null;
        VBox bubble = new VBox(4);
        bubble.setMaxWidth(520);

        boolean isGroup = currentChatUsername != null && currentChatUsername.length() == 24;

        ImageView senderAvatarView = null;
        if (sender == Sender.OTHER) {
            String actualSender = isGroup ? doc.getString("sender") : currentChatUsername;
            if (actualSender == null || actualSender.isBlank()) {
                actualSender = doc.getString("sender");
            }

            if (actualSender != null && !actualSender.isBlank()) {
                javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(10, 10, 10);
                senderAvatarView = new ImageView();
                senderAvatarView.setFitWidth(20);
                senderAvatarView.setFitHeight(20);
                senderAvatarView.setClip(clip);
                loadUserAvatarInto(senderAvatarView, actualSender, currentChatUsername);

                if (isGroup && !isUnsent) {
                    Label nameLbl = new Label(actualSender);
                    nameLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: -lm-muted; -fx-font-weight: 900; -fx-opacity: 0.8;");
                    bubble.getChildren().add(nameLbl);
                }
            }
        }

        boolean darkTheme = MainApp.currentTheme == MainApp.Theme.DARK;
        String bubbleTextColor = darkTheme ? "#FFFFFF" : "#000000";
        String bubbleMutedColor = darkTheme ? "rgba(255,255,255,0.82)" : "#475569";
        String bubbleBorderColor = darkTheme ? "rgba(255,255,255,0.16)" : "rgba(15,23,42,0.12)";
        String bubbleBackground = darkTheme ? "rgba(0,0,0,0.96)" : "rgba(255,255,255,0.98)";
        String bubbleShadow = darkTheme ? "rgba(0,0,0,0.45)" : "rgba(15,23,42,0.12)";

        String bubbleStyle =
                "-fx-background-color: " + bubbleBackground + ";" +
                "-fx-background-radius: 18;" +
                "-fx-border-color: " + bubbleBorderColor + ";" +
                "-fx-border-radius: 18;" +
                "-fx-border-width: 1;" +
                "-fx-padding: 11 14 11 14;" +
                "-fx-effect: dropshadow(three-pass-box, " + bubbleShadow + ", 12, 0.14, 0, 2);";
        bubble.setStyle(bubbleStyle);

        if (!isUnsent && replyName != null && replyText != null) {
            Label repNameLbl = new Label("Replied " );
            repNameLbl.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI Emoji', 'Segoe UI', 'Arial'; -fx-text-fill: " + bubbleTextColor + "; -fx-opacity: 0.85;");

            Label repTextLbl = new Label(replyText);
            repTextLbl.setStyle("-fx-font-size: 12px; -fx-font-family: 'Segoe UI Emoji', 'Segoe UI', 'Arial'; -fx-text-fill: " + bubbleMutedColor + "; -fx-opacity: 0.88; -fx-border-color: " + bubbleBorderColor + "; -fx-border-width: 0 0 0 2; -fx-padding: 0 0 0 6;");

            VBox repBox = new VBox(2, repNameLbl, repTextLbl);
            repBox.setStyle("-fx-padding: 0 0 4 0;");
            bubble.getChildren().add(repBox);
        }

        boolean isImage = doc.containsKey("isImage") && doc.getBoolean("isImage");
        boolean isFile = doc.containsKey("isFile") && doc.getBoolean("isFile");
        boolean isCallLog = Boolean.TRUE.equals(doc.getBoolean("isCallLog"));

        if (isUnsent) {
            String senderName = doc.getString("sender");
            String unsentText = sender == Sender.ME ? "You unsent a message" : (senderName + " unsent a message");
            Label unsentLbl = new Label(unsentText);
            unsentLbl.setWrapText(true);
            unsentLbl.setStyle("-fx-font-style: italic; -fx-font-size: 12.5px; -fx-text-fill: " + bubbleMutedColor + ";");
            bubble.getChildren().add(unsentLbl);
        } else if (isImage) {
            Image image = getCachedImage(text);
            if (image != null) {
                ImageView imgView = new ImageView(image);
                imgView.setFitWidth(240);
                imgView.setPreserveRatio(true);
                imgView.setSmooth(true);
                imgView.getStyleClass().add("message-image");
                imgView.setOnMouseClicked(e -> showImagePreview(image));
                bubble.getChildren().add(imgView);
            }
        }

        else if (isFile) {
            String fileName = doc.getString("fileName");

            HBox fileBox = new HBox(10);
            fileBox.setAlignment(Pos.CENTER_LEFT);
            fileBox.setStyle("-fx-padding: 8 10; -fx-background-color: " + (darkTheme ? "rgba(255,255,255,0.08);" : "rgba(15,23,42,0.04);") + " -fx-background-radius: 12;");

            // setStyle() এর বদলে সরাসরি setIconColor() ব্যবহার করা হলো
            FontIcon fileIcon = new FontIcon("mdi2f-file-document-outline");
            fileIcon.setIconSize(24);
            fileIcon.setIconColor(javafx.scene.paint.Color.web(bubbleTextColor));

            Label nameLabel = new Label(fileName);
            nameLabel.setWrapText(true);
            nameLabel.setMaxWidth(160);
            nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + bubbleTextColor + ";");

            Button downloadBtn = new Button();

            // ডাউনলোড আইকনের জন্যও setIconColor() ব্যবহার করা হলো
            FontIcon downloadIcon = new FontIcon("mdi2d-download-outline");
            downloadIcon.setIconSize(16);
            downloadIcon.setIconColor(javafx.scene.paint.Color.web(bubbleTextColor));

            downloadBtn.setGraphic(downloadIcon);
            downloadBtn.setStyle("-fx-background-color: " + (darkTheme ? "rgba(255,255,255,0.14);" : "rgba(15,23,42,0.10);") + " -fx-cursor: hand; -fx-background-radius: 8;");
            downloadBtn.setOnAction(e -> downloadFile(fileName, text));

            fileBox.getChildren().addAll(fileIcon, nameLabel, downloadBtn);
            bubble.getChildren().add(fileBox);
        }
        else {
            Node messageNode = createMessageTextNode(text, bubbleTextColor, darkTheme ? "#7DD3FC" : "#2563EB");
            bubble.getChildren().add(messageNode);
        }

        if (!isUnsent && Boolean.TRUE.equals(isEdited)) {
            Label editedLbl = new Label("(edited)");
            editedLbl.setStyle("-fx-font-size: 9.5px; -fx-text-fill: " + bubbleMutedColor + "; -fx-opacity: 0.75;");
            bubble.getChildren().add(editedLbl);
        }

        Button menuBtn = new Button("⋮");
        menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -lm-muted; -fx-cursor: hand; -fx-font-weight: bold; -fx-font-size: 16px;");
        menuBtn.setVisible(false);

        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("custom-menu");
        boolean allowMenu = !isUnsent;
        if (!allowMenu) {
            menuBtn.setManaged(false);
        } else {
            MenuItem replyItem = new MenuItem("Reply");
            replyItem.setOnAction(e -> {
                replyingName = sender == Sender.ME ? Session.getProfile().name : doc.getString("sender");
                replyingText = truncateText(text);
                actionTitle.setText("Selected for reply");
                actionSubtitle.setText(replyingText);
                actionIndicatorBox.setVisible(true);
                actionIndicatorBox.setManaged(true);
                Platform.runLater(() -> messageField.requestFocus());
            });

            MenuItem copyItem = new MenuItem("Copy");
            copyItem.setOnAction(e -> {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(text);
                clipboard.setContent(content);
            });

            menu.getItems().addAll(replyItem, copyItem);
        }

        if (allowMenu && sender == Sender.ME) {

            if (!isImage && !isFile && !isCallLog){
                MenuItem editItem = new MenuItem("Edit");
                editItem.setOnAction(e -> {
                    editingMsgId = msgId;
                    actionTitle.setText("Edit message");
                    actionSubtitle.setText(truncateText(text));
                    messageField.setText(text);
                    actionIndicatorBox.setVisible(true);
                    actionIndicatorBox.setManaged(true);
                });
                menu.getItems().add(editItem);
            }

            if (!isCallLog) {
                MenuItem unsendItem = new MenuItem("Unsend");
                unsendItem.setOnAction(e -> {
                    new Thread(() -> MessageService.unsendMessage(msgId)).start();
                });
                menu.getItems().add(unsendItem);
            }
        }

        if (allowMenu) {
            menuBtn.setOnMouseClicked(e -> menu.show(menuBtn, e.getScreenX(), e.getScreenY()));
        }

        HBox row = new HBox(5);
        row.setFillHeight(true);
        row.setPadding(new Insets(2, 14, 2, 14));
        row.setAlignment(sender == Sender.ME ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (sender == Sender.ME) {
            row.getChildren().addAll(menuBtn, bubble);
        } else {
            if (senderAvatarView != null) {
                VBox avatarWrap = new VBox(senderAvatarView);
                avatarWrap.setAlignment(Pos.TOP_LEFT);
                avatarWrap.setPadding(new Insets(7, 0, 0, 0));
                row.getChildren().add(avatarWrap);
            }
            row.getChildren().addAll(bubble, menuBtn);
        }

        if (allowMenu) {
            row.setOnMouseEntered(e -> menuBtn.setVisible(true));
            row.setOnMouseExited(e -> {

                if (!menu.isShowing()) {
                    menuBtn.setVisible(false);
                }
            });
        }

        if (allowMenu) {
            menu.setOnHidden(e -> {
                if (!row.isHover()) menuBtn.setVisible(false);
            });
        }

        messagesBox.getChildren().add(row);
        lastSender = sender;
        lastBubbleRow = row;
    }

    private Node createMessageTextNode(String message, String textColor, String linkColor) {
        String safeMessage = message == null ? "" : message;
        Matcher matcher = URL_PATTERN.matcher(safeMessage);
        if (!matcher.find()) {
            Label plain = new Label(safeMessage);
            plain.setWrapText(true);
            plain.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Segoe UI', 'Arial'; -fx-text-fill: " + textColor + ";");
            return plain;
        }

        matcher.reset();
        TextFlow flow = new TextFlow();
        flow.setMaxWidth(460);
        flow.setLineSpacing(1.2);

        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                Text plain = new Text(safeMessage.substring(cursor, matcher.start()));
                plain.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Segoe UI', 'Arial'; -fx-fill: " + textColor + ";");
                flow.getChildren().add(plain);
            }

            String token = matcher.group();
            String linkToken = token;
            String trailing = "";
            while (!linkToken.isEmpty() && ".,!?;:)".indexOf(linkToken.charAt(linkToken.length() - 1)) >= 0) {
                trailing = linkToken.charAt(linkToken.length() - 1) + trailing;
                linkToken = linkToken.substring(0, linkToken.length() - 1);
            }

            if (!linkToken.isEmpty()) {
                String openUrl = linkToken.startsWith("www.") ? "https://" + linkToken : linkToken;
                Text linkText = new Text(linkToken);
                linkText.setUnderline(true);
                linkText.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Segoe UI', 'Arial'; -fx-fill: " + linkColor + ";");
                linkText.setCursor(Cursor.HAND);
                linkText.setOnMouseClicked(e -> openExternalLink(openUrl));
                flow.getChildren().add(linkText);
            }

            if (!trailing.isEmpty()) {
                Text tailText = new Text(trailing);
                tailText.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Segoe UI', 'Arial'; -fx-fill: " + textColor + ";");
                flow.getChildren().add(tailText);
            }

            cursor = matcher.end();
        }

        if (cursor < safeMessage.length()) {
            Text remainder = new Text(safeMessage.substring(cursor));
            remainder.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Segoe UI', 'Arial'; -fx-fill: " + textColor + ";");
            flow.getChildren().add(remainder);
        }

        return flow;
    }

    private void openExternalLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        Thread openThread = new Thread(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Platform.runLater(() -> showCustomAlert("Open Link", "Opening links is not supported on this device.", true));
                    return;
                }
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ex) {
                Platform.runLater(() -> showCustomAlert("Open Link", "Could not open this link.", true));
            }
        }, "open-link");
        openThread.setDaemon(true);
        openThread.start();
    }

    private CustomMenuItem buildAttachmentItem(String title, String subtitle, String iconLiteral, Runnable action) {
        HBox row = new HBox(10);
        row.getStyleClass().add("attach-item");
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().add("attach-item-icon-wrap");
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(iconLiteral);
        icon.getStyleClass().add("attach-item-icon");
        iconWrap.getChildren().add(icon);

        VBox textBox = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("attach-item-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("attach-item-subtitle");
        textBox.getChildren().addAll(titleLabel, subtitleLabel);

        row.getChildren().addAll(iconWrap, textBox);

        CustomMenuItem item = new CustomMenuItem(row, true);
        item.setHideOnClick(true);
        item.setOnAction(e -> action.run());
        return item;
    }

    @FXML
    public void onAttachmentClick() {
        if (currentChatUsername == null || isChatBlocked) return;

        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("attach-menu");
        menu.setAutoHide(true);

        CustomMenuItem photoItem = buildAttachmentItem("Send Photo", "JPG or PNG images", "mdi2c-camera-outline", this::handleSendPhoto);
        CustomMenuItem fileItem = buildAttachmentItem("Send File", "Max size 2 MB", "mdi2f-file-document-outline", this::handleSendFile);

        menu.getItems().addAll(photoItem, fileItem);

        menu.show(attachmentBtn, javafx.geometry.Side.TOP, 0, 10);
    }

    private void handleSendPhoto() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        java.io.File file = fileChooser.showOpenDialog(messageField.getScene().getWindow());
        if (file != null) uploadAndSendImage(file);
    }

    private void handleSendFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        java.io.File file = fileChooser.showOpenDialog(messageField.getScene().getWindow());

        if (file != null) {
            UserProfile me = Session.getProfile();
            if (me == null || currentChatUsername == null) return;
            if (file.length() > MAX_FILE_SIZE_BYTES) {
                ThemedDialogs.showAlert(
                        messageField == null ? null : messageField.getScene().getWindow(),
                        "File Too Large",
                        "File is too large. The maximum attachment size is 2 MB.",
                        true
                );
                return;
            }

            String receiver = currentChatUsername;
            String sender = me.username;
            String fileName = file.getName();
            String finalRepName = replyingName;
            String finalRepText = replyingText;

            cancelAction();

            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
                    String base64Data = java.util.Base64.getEncoder().encodeToString(fileContent);
                    MessageService.sendFileMessage(sender, receiver, base64Data, fileName, finalRepName, finalRepText);
                    return true;
                }
            };
            task.setOnSucceeded(e -> Platform.runLater(this::loadRecentChats));
            Thread uploadThread = new Thread(task, "file-upload-thread");
            uploadThread.setDaemon(true);
            uploadThread.start();
        }
    }

    private void downloadFile(String fileName, String base64Data) {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setInitialFileName(fileName);
        java.io.File file = fileChooser.showSaveDialog(messagesScroll.getScene().getWindow());

        if (file != null) {
            try {
                byte[] data = java.util.Base64.getDecoder().decode(base64Data);
                java.nio.file.Files.write(file.toPath(), data);

                Platform.runLater(() -> showCustomAlert("Download Complete", "File saved successfully to your computer.", false));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void uploadAndSendImage(java.io.File file) {
        UserProfile me = Session.getProfile();
        if (me == null || currentChatUsername == null) return;

        String receiver = currentChatUsername;
        String sender = me.username;
        String finalRepName = replyingName;
        String finalRepText = replyingText;

        cancelAction();

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {

                byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
                if (fileContent.length > 800 * 1024) {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file);
                    int newWidth = 800;
                    int newHeight = (img.getHeight() * newWidth) / img.getWidth();

                    java.awt.image.BufferedImage scaledImg = new java.awt.image.BufferedImage(newWidth, newHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g2d = scaledImg.createGraphics();
                    g2d.drawImage(img, 0, 0, newWidth, newHeight, null);
                    g2d.dispose();

                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(scaledImg, "jpg", baos);
                    fileContent = baos.toByteArray();
                }

                String base64Image = java.util.Base64.getEncoder().encodeToString(fileContent);
                String encodedImage = java.net.URLEncoder.encode(base64Image, java.nio.charset.StandardCharsets.UTF_8);

                String IMGBB_API_KEY = "17693c9a056e83534dd33131bcd568c9";
                String formData = "key=" + IMGBB_API_KEY + "&image=" + encodedImage + "&expiration=172800";

                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://api.imgbb.com/1/upload"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(formData))
                        .build();

                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                String resBody = response.body();

                if (resBody.contains("\"url\":\"")) {
                    int start = resBody.indexOf("\"url\":\"") + 7;
                    int end = resBody.indexOf("\"", start);
                    String imgUrl = resBody.substring(start, end).replace("\\/", "/");

                    MessageService.sendImageMessage(sender, receiver, imgUrl, finalRepName, finalRepText);
                    return true;
                }
                return false;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> loadRecentChats()));
        new Thread(task, "photo-upload-thread").start();
    }

    @FXML
    public void onSidebarEnter() {
        mouseOverSidebar = true;
        if (collapseDelay != null) collapseDelay.stop();
        if (drawerOpen) return;
        if (themeSwitching) return;
        if (sidebarBusy) return;
        expandSidebar();
    }

    @FXML
    public void onSidebarExit() {
        mouseOverSidebar = false;
        if (drawerOpen) return;
        if (themeSwitching) return;
        if (sidebarBusy) return;

        if (collapseDelay != null) collapseDelay.playFromStart();
    }

    private void expandSidebar() {
        if (expanded) return;
        expanded = true;

        if (sidebar != null) {
            sidebar.getStyleClass().remove("collapsed");
            if (!sidebar.getStyleClass().contains("expanded")) sidebar.getStyleClass().add("expanded");
        }

        animateSidebarAndPills(EXPANDED_W, PILL_EXPANDED_W, () -> setNavTextVisible(true));
    }

    private void collapseSidebar() {
        if (!expanded) return;
        expanded = false;

        setNavTextVisible(false);

        if (sidebar != null) {
            sidebar.getStyleClass().remove("expanded");
            if (!sidebar.getStyleClass().contains("collapsed")) sidebar.getStyleClass().add("collapsed");
        }

        animateSidebarAndPills(COLLAPSED_W, PILL_COLLAPSED_W, null);
    }

    private void animateSidebarAndPills(double targetSidebarW, double targetPillW, Runnable onFinished) {
        if (sidebar == null) return;

        if (sidebarAnim != null) sidebarAnim.stop();
        sidebarBusy = true;

        KeyValue kvSidebar = new KeyValue(sidebar.prefWidthProperty(), targetSidebarW, Interpolator.EASE_BOTH);

        var pills = sidebar.lookupAll(".nav-bg");
        KeyValue[] kvPills = pills.stream().filter(n -> n instanceof Region).map(n -> new KeyValue(((Region) n).prefWidthProperty(), targetPillW, Interpolator.EASE_BOTH)).toArray(KeyValue[]::new);

        KeyValue[] all = new KeyValue[1 + kvPills.length];
        all[0] = kvSidebar;
        System.arraycopy(kvPills, 0, all, 1, kvPills.length);

        sidebarAnim = new Timeline(new KeyFrame(ANIM_DUR, all));

        sidebarAnim.setOnFinished(e -> {
            sidebarBusy = false;
            if (onFinished != null) onFinished.run();
        });
        sidebarAnim.play();
    }

    private void setNavTextVisible(boolean visible) {
        if (sidebar == null) return;

        for (javafx.scene.Node n : sidebar.lookupAll(".nav-text")) {
            if (n instanceof Label label) {
                Object original = label.getProperties().get("navOriginalText");
                if (original == null) {
                    label.getProperties().put("navOriginalText", label.getText());
                    original = label.getText();
                }

                label.setTextOverrun(OverrunStyle.CLIP);
                label.setMaxWidth(visible ? Double.MAX_VALUE : 0);
                label.setOpacity(visible ? 1 : 0);
                label.setText(visible ? String.valueOf(original) : "");
            }
            n.setVisible(visible);
            n.setManaged(visible);
        }
    }

    private void setNavPillWidth(double w) {
        if (sidebar == null) return;

        for (javafx.scene.Node n : sidebar.lookupAll(".nav-bg")) {
            if (n instanceof Region r) {
                r.setPrefWidth(w);
            }
        }
    }

    private void syncSidebarAfterTheme() {
        if (sidebar == null) return;

        if (sidebarAnim != null) sidebarAnim.stop();

        sidebar.applyCss();
        sidebar.layout();

        sidebar.getStyleClass().removeAll("expanded", "collapsed");
        sidebar.getStyleClass().add(expanded ? "expanded" : "collapsed");

        sidebar.setPrefWidth(expanded ? EXPANDED_W : COLLAPSED_W);
        setNavPillWidth(expanded ? PILL_EXPANDED_W : PILL_COLLAPSED_W);
        setNavTextVisible(expanded);
    }

    @FXML
    public void onChatClick() {
        setActive(chatBtn, chatBtn, archiveBtn, profileBtn, settingsBtn);
        showingArchived = false;
        applyCachedRecentChats();
        loadRecentChats();
    }
    @FXML
    private void onFriendsClick(ActionEvent event) {

        try {
            Stage stage = (Stage) btnFriends.getScene().getWindow();
            Scene scene = stage.getScene();

            Parent root = new FXMLLoader(MainApp.class.getResource("/friends.fxml")).load();
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
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void onMomentsClick(ActionEvent event) {
        try {
            Stage stage = (Stage) btnMoments.getScene().getWindow();
            Scene scene = stage.getScene();

            Parent root = new FXMLLoader(MainApp.class.getResource("/moments.fxml")).load();
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
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void onArchiveClick() {
        setActive(archiveBtn, chatBtn, archiveBtn, profileBtn, settingsBtn);
        showingArchived = true;
        applyCachedRecentChats();
        loadRecentChats();
    }
    @FXML
    public void onOpenSettings() {
        setActive(settingsBtn, chatBtn, archiveBtn, profileBtn, settingsBtn);
        expandSidebar();
        openDrawer(DrawerMode.SETTINGS);
    }

    @FXML
    public void onOpenProfile() {

        try {
            Stage stage = (Stage) profileBtn.getScene().getWindow();
            Scene scene = stage.getScene();

            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/profile.fxml"));
            Parent profileRoot = loader.load();
            profileRoot.setOpacity(0);

            FadeTransition out = new FadeTransition(Duration.millis(200), scene.getRoot());
            out.setFromValue(1);
            out.setToValue(0);
            out.setInterpolator(Interpolator.EASE_BOTH);

            out.setOnFinished(ev -> {
                scene.setRoot(profileRoot);
                scene.getStylesheets().clear();

                if (MainApp.class.getResource("/main.css") != null) {
                    scene.getStylesheets().add(MainApp.class.getResource("/main.css").toExternalForm());
                }

                ThemeManager.applyMainTheme(scene);

                FadeTransition in = new FadeTransition(Duration.millis(250), profileRoot);
                in.setFromValue(0);
                in.setToValue(1);
                in.setInterpolator(Interpolator.EASE_BOTH);
                in.play();
            });

            out.play();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Could not open Profile page.");
        }
    }

    @FXML
    public void onCloseSettingsDrawer() {
        closeSettingsDrawer();
    }

    @FXML
    public void onToggleActiveSection() {
        toggleSection(activeSection, activeChevron, true);
        if (isSectionOpen(activeSection)) closeSection(themeSection, themeChevron);
    }

    @FXML
    public void onToggleThemeSection() {
        toggleSection(themeSection, themeChevron, true);
        if (isSectionOpen(themeSection)) closeSection(activeSection, activeChevron);
    }

    @FXML
    public void onLogout() {
        showLogoutModal(true);
    }

    @FXML
    public void onCancelLogout() {
        showLogoutModal(false);
    }

    @FXML
    public void onConfirmLogout() {
        shutdownBackgroundWork();
        showLogoutModal(false);
        Session.clear();
        goToLogin();
    }

    private void showLogoutModal(boolean show) {
        if (logoutOverlay == null) return;

        logoutOverlay.setManaged(show);
        logoutOverlay.setVisible(show);

        if (appRoot != null) {
            appRoot.setEffect(show ? new GaussianBlur(18) : null);
            appRoot.setDisable(show);
        }
        if (!show && appRoot != null) {
            appRoot.setDisable(false);
        }
    }

    private void goToLogin() {
        try {
            Scene scene = (sidebar != null && sidebar.getScene() != null) ? sidebar.getScene() : (appRoot != null ? appRoot.getScene() : null);
            if (scene == null) return;

            Stage stage = (Stage) scene.getWindow();

            Parent loginRoot = FXMLLoader.load(Objects.requireNonNull(MainApp.class.getResource("/login.fxml")));
            loginRoot.setOpacity(0);

            Parent currentRoot = scene.getRoot();

            FadeTransition out = new FadeTransition(Duration.millis(220), currentRoot);
            out.setFromValue(1);
            out.setToValue(0);
            out.setInterpolator(Interpolator.EASE_BOTH);

            out.setOnFinished(e -> {
                scene.setRoot(loginRoot);

                scene.getStylesheets().clear();
                if (MainApp.class.getResource("/login.css") != null) {
                    scene.getStylesheets().add(MainApp.class.getResource("/login.css").toExternalForm());
                }

                FadeTransition in = new FadeTransition(Duration.millis(260), loginRoot);
                in.setFromValue(0);
                in.setToValue(1);
                in.setInterpolator(Interpolator.EASE_BOTH);
                in.play();
            });

            out.play();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void initSettingsDrawer() {
        if (settingsDrawer != null) {
            settingsDrawer.setManaged(false);
            settingsDrawer.setVisible(false);
            settingsDrawer.setTranslateX(-DRAWER_W);
            settingsDrawer.setOpacity(1);
        }

        if (activeSection != null) {
            activeSection.setManaged(false);
            activeSection.setVisible(false);
            activeSection.setMaxHeight(0);
            activeSection.setOpacity(0);
        }
        if (themeSection != null) {
            themeSection.setManaged(false);
            themeSection.setVisible(false);
            themeSection.setMaxHeight(0);
            themeSection.setOpacity(0);
        }

        setupSwitch(activeToggle, activeThumb);

        themeGroup = new ToggleGroup();
        if (lightRadio != null) lightRadio.setToggleGroup(themeGroup);
        if (darkRadio != null) darkRadio.setToggleGroup(themeGroup);

        if (MainApp.currentTheme == MainApp.Theme.DARK) {
            if (darkRadio != null) darkRadio.setSelected(true);
        } else {
            if (lightRadio != null) lightRadio.setSelected(true);
        }

        if (themeGroup != null) {
            themeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
                if (newV == null) return;

                themeSwitching = true;

                if (darkRadio != null && darkRadio.isSelected()) {
                    MainApp.currentTheme = MainApp.Theme.DARK;
                } else {
                    MainApp.currentTheme = MainApp.Theme.LIGHT;
                }

                Scene scene = settingsDrawer != null && settingsDrawer.getScene() != null
                        ? settingsDrawer.getScene()
                        : (sidebar != null ? sidebar.getScene() : null);

                if (scene == null) {
                    themeSwitching = false;
                    return;
                }

                FadeTransition fadeOut = new FadeTransition(Duration.millis(120), scene.getRoot());
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0.94);
                fadeOut.setInterpolator(Interpolator.EASE_BOTH);
                fadeOut.setOnFinished(ev -> {
                    ThemeManager.applyMainTheme(scene);
                    sidebarBusy = true;
                    syncSidebarAfterTheme();
                    sidebarBusy = false;
                    if (currentChatUsername != null && !currentChatUsername.isBlank()) {
                        loadRecentChats();
                    }

                    FadeTransition fadeIn = new FadeTransition(Duration.millis(180), scene.getRoot());
                    fadeIn.setFromValue(0.94);
                    fadeIn.setToValue(1);
                    fadeIn.setInterpolator(Interpolator.EASE_BOTH);
                    fadeIn.setOnFinished(done -> themeSwitching = false);
                    fadeIn.play();
                });
                fadeOut.play();
            });
        }
    }

    private void initProfilePanel() {

        if (profileSection != null) {
            profileSection.setManaged(false);
            profileSection.setVisible(false);
            profileSection.setMaxHeight(0);
            profileSection.setOpacity(0);
        }

        UserProfile p = Session.getProfile();
        if (p != null) {
            if (profileNameField != null) profileNameField.setText(p.name == null || p.name.isBlank() ? "—" : p.name);
            if (profileEmailValue != null)
                profileEmailValue.setText(p.email == null || p.email.isBlank() ? "—" : p.email);

            if (profileUsernameField != null) profileUsernameField.setText(p.username == null ? "" : p.username);
            if (profileBirthdatePicker != null) profileBirthdatePicker.setValue(p.birthdate);
        } else {
            if (profileNameField != null) profileNameField.setText("—");
            if (profileEmailValue != null) profileEmailValue.setText("—");
        }

        if (profileStatusLabel != null) profileStatusLabel.setText("");
    }

    @FXML
    public void onToggleProfileSection() {
        toggleSection(profileSection, profileChevron, true);
        if (isSectionOpen(profileSection)) {

            closeSection(activeSection, activeChevron);
            closeSection(themeSection, themeChevron);
        }
    }

    @FXML
    public void onSaveProfile() {

    }

    private void openDrawer(DrawerMode mode) {
        if (settingsDrawer == null) return;
        if (!expanded) expandSidebar();

        drawerMode = mode;
        applyDrawerMode(mode);

        if (drawerOpen) return;

        drawerOpen = true;

        if (drawerAnim != null) drawerAnim.stop();

        settingsDrawer.setManaged(true);
        settingsDrawer.setVisible(true);
        settingsDrawer.setTranslateX(-DRAWER_W);

        drawerAnim = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(settingsDrawer.translateXProperty(), -DRAWER_W, Interpolator.EASE_OUT)), new KeyFrame(DRAWER_ANIM_DUR, new KeyValue(settingsDrawer.translateXProperty(), 0, Interpolator.EASE_OUT)));
        drawerAnim.play();
    }

    private void applyDrawerMode(DrawerMode mode) {
        if (drawerTitleLabel != null) {
            drawerTitleLabel.setText(mode == DrawerMode.PROFILE ? "Profile" : "Settings");
        }

        closeSection(activeSection, activeChevron);
        closeSection(themeSection, themeChevron);

        if (mode == DrawerMode.PROFILE) {

            setNodeVisibleManaged(profileItemBtn, false);
            setNodeVisibleManaged(activeItemBtn, false);
            setNodeVisibleManaged(themeItemBtn, false);
            setNodeVisibleManaged(blockedItemBtn, false);
            setNodeVisibleManaged(logoutItemBtn, false);

            if (profileSection != null) {
                openSection(profileSection, profileChevron, false);
            }
        } else {

            setNodeVisibleManaged(profileItemBtn, false);
            setNodeVisibleManaged(activeItemBtn, true);
            setNodeVisibleManaged(themeItemBtn, true);
            setNodeVisibleManaged(blockedItemBtn, true);
            setNodeVisibleManaged(logoutItemBtn, true);

            closeSection(profileSection, profileChevron);
            if (profileChevron != null) profileChevron.setRotate(0);
        }
    }

    private void setNodeVisibleManaged(javafx.scene.Node n, boolean visible) {
        if (n == null) return;
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private void closeSettingsDrawer() {
        if (settingsDrawer == null) return;
        if (!drawerOpen) return;

        drawerOpen = false;

        if (drawerAnim != null) drawerAnim.stop();

        closeSection(activeSection, activeChevron);
        closeSection(themeSection, themeChevron);
        closeSection(profileSection, profileChevron);

        drawerAnim = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(settingsDrawer.translateXProperty(), settingsDrawer.getTranslateX(), Interpolator.EASE_IN)), new KeyFrame(DRAWER_ANIM_DUR, new KeyValue(settingsDrawer.translateXProperty(), -DRAWER_W, Interpolator.EASE_IN)));
        drawerAnim.setOnFinished(e -> {
            settingsDrawer.setVisible(false);
            settingsDrawer.setManaged(false);

            drawerMode = DrawerMode.SETTINGS;
            applyDrawerMode(DrawerMode.SETTINGS);

            if (!mouseOverSidebar) {
                Platform.runLater(() -> {
                    if (!sidebarBusy && !drawerOpen) collapseSidebar();
                });
            }
        });

        drawerAnim.play();
    }

    private boolean isSectionOpen(VBox section) {
        return section != null && section.isManaged() && section.isVisible() && section.getMaxHeight() > 0;
    }

    private void toggleSection(VBox section, FontIcon chevron, boolean animateChevron) {
        if (section == null) return;

        if (isSectionOpen(section)) {
            closeSection(section, chevron);
        } else {
            openSection(section, chevron, animateChevron);
        }
    }

    private void openSection(VBox section, FontIcon chevron, boolean animateChevron) {
        if (section == null) return;

        if (sectionAnim != null) sectionAnim.stop();

        section.setManaged(true);
        section.setVisible(true);

        section.applyCss();
        section.layout();
        double targetH = section.prefHeight(-1);
        if (targetH < 1) targetH = 60;

        KeyValue kvH = new KeyValue(section.maxHeightProperty(), targetH, Interpolator.EASE_BOTH);
        KeyValue kvO = new KeyValue(section.opacityProperty(), 1, Interpolator.EASE_BOTH);

        if (animateChevron && chevron != null) {
            KeyValue kvR = new KeyValue(chevron.rotateProperty(), 180, Interpolator.EASE_BOTH);
            sectionAnim = new Timeline(new KeyFrame(SECTION_ANIM_DUR, kvH, kvO, kvR));
        } else {
            sectionAnim = new Timeline(new KeyFrame(SECTION_ANIM_DUR, kvH, kvO));
        }

        sectionAnim.play();
    }

    private void closeSection(VBox section, FontIcon chevron) {
        if (section == null) return;

        if (sectionAnim != null) sectionAnim.stop();

        KeyValue kvH = new KeyValue(section.maxHeightProperty(), 0, Interpolator.EASE_BOTH);
        KeyValue kvO = new KeyValue(section.opacityProperty(), 0, Interpolator.EASE_BOTH);

        Timeline t;
        if (chevron != null) {
            KeyValue kvR = new KeyValue(chevron.rotateProperty(), 0, Interpolator.EASE_BOTH);
            t = new Timeline(new KeyFrame(SECTION_ANIM_DUR, kvH, kvO, kvR));
        } else {
            t = new Timeline(new KeyFrame(SECTION_ANIM_DUR, kvH, kvO));
        }

        t.setOnFinished(e -> {
            section.setVisible(false);
            section.setManaged(false);
        });

        t.play();
    }

    private void setupSwitch(ToggleButton toggle, Region thumb) {
        if (toggle == null || thumb == null) return;

        updateThumbPosition(thumb, toggle.isSelected(), false);
        toggle.selectedProperty().addListener((obs, oldV, newV) -> updateThumbPosition(thumb, newV, true));
    }

    private void updateThumbPosition(Region thumb, boolean on, boolean animate) {
        if (thumb == null) return;

        double target = on ? 8 : -8;

        if (!animate) {
            thumb.setTranslateX(target);
            return;
        }

        Timeline t = new Timeline(new KeyFrame(SWITCH_ANIM_DUR, new KeyValue(thumb.translateXProperty(), target, Interpolator.EASE_BOTH)));
        t.play();
    }

    @FXML
    public void onSend() {
        if (isChatBlocked) return;
        String msg = (messageField == null || messageField.getText() == null) ? "" : messageField.getText().trim();
        if (msg.isEmpty() || currentChatUsername == null) return;

        UserProfile me = Session.getProfile();
        if (me == null || me.username == null) return;

        String myUsername = me.username;
        String receiverUsername = currentChatUsername;

        String finalEditId = editingMsgId;
        String finalRepName = replyingName;
        String finalRepText = replyingText;

        cancelAction();

        new Thread(() -> {
            if (finalEditId != null) {
                MessageService.editMessage(finalEditId, msg);
            } else {
                MessageService.sendMessage(myUsername, receiverUsername, msg, finalRepName, finalRepText);
            }
            Platform.runLater(() -> {
                searchField.clear();
                loadRecentChats();
            });
        }, "send-message-task").start();
    }

    private void setActive(Button btn, Button... all) {
        if (all != null) {
            for (Button b : all) {
                if (b != null) b.getStyleClass().remove("active");
            }
        }
        if (btn != null && !btn.getStyleClass().contains("active")) {
            btn.getStyleClass().add("active");
        }
    }

    @FXML
    public void onOpenBlockedUsers() {
        try {
            Stage stage = (Stage) blockedItemBtn.getScene().getWindow();
            Scene scene = stage.getScene();
            Parent root = new FXMLLoader(MainApp.class.getResource("/blocked_users.fxml")).load();
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
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadRecentChats() {
        loadRecentChats(null);
    }

    private void loadRecentChats(List<String> preloadedPartners) {
        UserProfile me = Session.getProfile();
        if (me == null || me.username == null) return;

        final String[] inboxState = new String[1];
        Task<List<String>> loadTask = new Task<>() {
            @Override
            protected List<String> call() {
                List<String> allPartners = preloadedPartners == null
                        ? MessageService.getRecentChatPartners(me.username)
                        : new ArrayList<>(preloadedPartners);
                inboxState[0] = String.join("|", allPartners);

                List<String> archivedUsers = UserService.getArchivedPartners(me.username);
                List<String> filteredList = new ArrayList<>();
                for (String partnerStr : allPartners) {
                    String displayPart = partnerStr.split(":::")[0];
                    String username;
                    if (displayPart.contains(" (@") && displayPart.endsWith(")")) {
                        username = displayPart.substring(displayPart.lastIndexOf(" (@") + 3, displayPart.length() - 1);
                    } else {
                        username = displayPart;
                    }

                    boolean isArchived = archivedUsers.contains(username);
                    if (showingArchived && isArchived) filteredList.add(partnerStr);
                    else if (!showingArchived && !isArchived) filteredList.add(partnerStr);
                }
                return filteredList;
            }
        };

        loadTask.setOnSucceeded(ev -> {
            if (inboxState[0] != null) {
                lastInboxState = inboxState[0];
            }
            List<String> partners = loadTask.getValue();
            updateRecentChatsCache(partners);
            applyRecentChatsToUi(partners);
        });

        Thread loadThread = new Thread(loadTask, "load-recent-chats");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void applyRecentChatsToUi(List<String> partners) {
        allInboxes.setAll(partners);
        if (filteredInboxes != null) {
            filteredInboxes.setPredicate(s -> true);
        }

        String pending = pendingOpenChatUsername;
        if (pending != null && !pending.isBlank()) {
            for (String partner : partners) {
                String displayPart = partner.split(":::")[0];
                String username = displayPart;
                if (displayPart.contains(" (@") && displayPart.endsWith(")")) {
                    username = displayPart.substring(displayPart.lastIndexOf(" (@") + 3, displayPart.length() - 1);
                }
                if (pending.equals(username)) {
                    pendingOpenChatUsername = null;
                    selectInbox(partner);
                    return;
                }
            }
            pendingOpenChatUsername = null;
        }

        if (currentChatUsername == null) {
            if (!partners.isEmpty()) {
                selectInbox(partners.get(0));
            } else {
                showEmptyChatState(showingArchived);
            }
            return;
        }

        if (usersList != null) {
            for (int i = 0; i < partners.size(); i++) {
                String displayPart = partners.get(i).split(":::")[0];
                if (displayPart.contains("(@" + currentChatUsername + ")")) {
                    usersList.getSelectionModel().select(i);
                    return;
                }
            }
        }
    }

    @FXML
    public void onAudioCallClick() {
        startCall("audio");
    }

    @FXML
    public void onVideoCallClick() {
        startCall("video");
    }

    private void startCall(String callType) {
        if (currentChatUsername == null) return;
        if (isChatBlocked) return;
        if (isGroupChatSelection(currentChatUsername)) return;

        UserProfile me = Session.getProfile();
        if (me == null) return;

        boolean success = MessageService.initiateCall(me.username, currentChatUsername, callType);

        if (success) {
            Platform.runLater(() -> openCallWindow(currentChatUsername, callType, true, null));
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING, "User is currently busy!");
            alert.show();
        }
    }

    @FXML
    public void onOpenCreateGroup(ActionEvent event) {
        UserProfile me = Session.getProfile();
        if (me == null) return;

        Stage groupStage = new Stage();
        groupStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        groupStage.initStyle(javafx.stage.StageStyle.UNDECORATED);

        VBox root = new VBox(20);
        root.setPadding(new Insets(28));

        root.setStyle("-fx-background-color: -lm-panel; -fx-background-radius: 24; -fx-border-radius: 24; -fx-border-color: rgba(148,163,184,0.25); -fx-border-width: 1; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 30, 0, 0, 10);");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Create Group");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 900; -fx-text-fill: -lm-text;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -lm-muted; -fx-font-size: 18px; -fx-cursor: hand; -fx-font-weight: bold;");
        closeBtn.setOnAction(e -> groupStage.close());
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -lm-text; -fx-font-size: 18px; -fx-cursor: hand; -fx-font-weight: bold;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -lm-muted; -fx-font-size: 18px; -fx-cursor: hand; -fx-font-weight: bold;"));
        header.getChildren().addAll(title, spacer, closeBtn);

        TextField groupNameField = new TextField();
        groupNameField.setPromptText("Enter group name");
        groupNameField.setStyle("-fx-padding: 12 16; -fx-background-radius: 12; -fx-border-color: -lm-border; -fx-border-radius: 12; -fx-background-color: -lm-soft; -fx-text-fill: -lm-text; -fx-font-size: 14px; -fx-font-weight: bold;");

        TextField searchField = new TextField();
        searchField.setPromptText("Search friends to add...");
        searchField.setStyle("-fx-padding: 10 16; -fx-background-radius: 20; -fx-border-color: transparent; -fx-background-color: -lm-bg; -fx-text-fill: -lm-text; -fx-font-size: 13px;");

        Label errorLabel = new Label("Minimum 3 members required!");
        errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12.5px; -fx-font-weight: bold;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        VBox friendsList = new VBox(6);
        friendsList.setPadding(new Insets(5, 10, 5, 0));
        ScrollPane scrollPane = new ScrollPane(friendsList);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(240);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        SmoothScrollUtil.apply(scrollPane);

        List<String> selectedMembers = new ArrayList<>();
        selectedMembers.add(me.username);

        Task<List<Document>> loadTask = new Task<>() {
            @Override
            protected List<Document> call() {
                List<Document> allUsers = new ArrayList<>();
                MongoDatabaseService.getDatabase().getCollection("users").find().into(allUsers);
                return allUsers;
            }
        };
        loadTask.setOnSucceeded(e -> {
            for (Document user : loadTask.getValue()) {
                String uName = user.getString("username");
                if (uName.equals(me.username)) continue;

                CheckBox checkBox = new CheckBox(user.getString("name") + " (@" + uName + ")");
                String baseStyle = "-fx-text-fill: -lm-text; -fx-font-size: 14px; -fx-font-weight: 700; -fx-cursor: hand; -fx-padding: 10 12; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: transparent; -fx-border-width: 1;";
                String hoverStyle = baseStyle + " -fx-background-color: -lm-hover; -fx-border-color: rgba(148,163,184,0.35);";
                String selectedStyle = baseStyle + " -fx-background-color: rgba(33,199,182,0.18); -fx-border-color: rgba(33,199,182,0.62);";
                checkBox.setStyle(baseStyle);
                checkBox.setMaxWidth(Double.MAX_VALUE);
                checkBox.setGraphicTextGap(10);

                checkBox.setOnMouseEntered(ev -> { if (!checkBox.isSelected()) checkBox.setStyle(hoverStyle); });
                checkBox.setOnMouseExited(ev -> { if (!checkBox.isSelected()) checkBox.setStyle(baseStyle); });

                checkBox.setOnAction(ev -> {
                    if (checkBox.isSelected()) {
                        selectedMembers.add(uName);
                        checkBox.setStyle(selectedStyle);
                    } else {
                        selectedMembers.remove(uName);
                        checkBox.setStyle(baseStyle);
                    }
                    errorLabel.setVisible(false);
                    errorLabel.setManaged(false);
                });
                friendsList.getChildren().add(checkBox);
            }
        });
        new Thread(loadTask).start();

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            String query = newValue.trim().toLowerCase();
            for (javafx.scene.Node node : friendsList.getChildren()) {
                if (node instanceof CheckBox checkBox) {
                    boolean match = checkBox.getText().toLowerCase().contains(query);
                    checkBox.setVisible(match);
                    checkBox.setManaged(match);
                }
            }
        });

        Button createBtn = new Button("Create Group");
        createBtn.setStyle("-fx-background-color: linear-gradient(to right, #111827, #1f2937); -fx-text-fill: white; -fx-font-weight: 900; -fx-font-size: 14px; -fx-padding: 12; -fx-background-radius: 12; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.2), 15, 0.15, 0, 4);");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnMouseEntered(e -> createBtn.setOpacity(0.9));
        createBtn.setOnMouseExited(e -> createBtn.setOpacity(1.0));

        createBtn.setOnAction(e -> {
            String gName = groupNameField.getText().trim();
            if (gName.isEmpty()) {
                errorLabel.setText("Please enter a group name!");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                return;
            }
            if (selectedMembers.size() < 3) {
                errorLabel.setText("Minimum 3 members required!");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                return;
            }
            String groupId = GroupService.createGroup(gName, me.username, selectedMembers);
            if (groupId != null) {
                groupStage.close();
                loadRecentChats();
            }
        });

        root.getChildren().addAll(header, groupNameField, searchField, scrollPane, errorLabel, createBtn);

        StackPane transparentLayer = new StackPane(root);
        transparentLayer.setStyle("-fx-background-color: transparent; -fx-padding: 15;");

        Scene scene = new Scene(transparentLayer, 420, 560);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        ThemeManager.applyMainTheme(scene);
        groupStage.setScene(scene);
        groupStage.show();
    }

    private void showCustomAlert(String titleText, String messageText, boolean isWarning) {
        ThemedDialogs.showAlert(
                messagesScroll == null ? null : messagesScroll.getScene().getWindow(),
                titleText,
                messageText,
                isWarning
        );
    }
}

