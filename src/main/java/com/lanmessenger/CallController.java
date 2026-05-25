package com.lanmessenger;

import com.mongodb.client.model.Filters;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.bson.Document;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CallController {

    @FXML
    private ImageView remoteVideo;
    @FXML
    private ImageView avatarImage;
    @FXML
    private Label nameLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label callStateHint;
    @FXML
    private Label callTimerLabel;
    @FXML
    private Button endBtn;
    @FXML
    private ToggleButton micBtn;
    @FXML
    private ToggleButton camBtn;
    @FXML
    private HBox incomingActions;
    @FXML
    private HBox inCallActions;
    @FXML
    private VBox avatarBox;

    private String partnerUsername;
    private String callType;
    private boolean isCaller;
    private String callerIp;
    private boolean inCall = false;
    private boolean localHasVideo = false;
    private boolean localHasMic = false;
    private long callStartMs = 0L;
    private volatile boolean callAnswered = false;
    private volatile boolean isCallEnded = false;
    private volatile boolean isWindowOpen = true;
    private Timeline callTimerTimeline;
    private Timeline ringingPulseTimeline;
    private static final Map<String, String> USER_PROFILE_PIC_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Image> AVATAR_IMAGE_CACHE = new ConcurrentHashMap<>();
    private static volatile Image fallbackAvatarImage;

    public void setupCall(String partner, String type, boolean caller, String ip) {
        partnerUsername = partner;
        callType = type;
        isCaller = caller;
        callerIp = ip;

        nameLabel.setText(partner);
        updateCallStatusText(caller ? "Ringing" : "Incoming call");
        loadAvatar(partner);
        applyAvatarClip();

        boolean isVideo = "video".equalsIgnoreCase(type);
        localHasVideo = isVideo;
        localHasMic = true;

        camBtn.setVisible(isVideo);
        camBtn.setManaged(isVideo);
        remoteVideo.setVisible(isVideo);
        remoteVideo.setManaged(isVideo);
        if (avatarBox != null) {
            avatarBox.setVisible(true);
            avatarBox.setManaged(true);
        }

        incomingActions.setVisible(!caller);
        incomingActions.setManaged(!caller);
        inCallActions.setVisible(caller);
        inCallActions.setManaged(caller);
        if (callTimerLabel != null) {
            callTimerLabel.setVisible(false);
            callTimerLabel.setManaged(false);
            callTimerLabel.setText("00:00");
        }

        startRingingPulse();

        if (caller) {
            startMedia();
        }

        startCallStatusMonitor();
    }

    private void startCallStatusMonitor() {
        Thread monitor = new Thread(() -> {
            try {
                while (isWindowOpen && !isCallEnded) {
                    Thread.sleep(1000);
                    UserProfile me = Session.getProfile();
                    if (me == null) {
                        break;
                    }

                    Document doc = MongoDatabaseService.getDatabase().getCollection("calls")
                            .find(Filters.or(
                                    Filters.and(Filters.eq("caller", me.username), Filters.eq("receiver", partnerUsername)),
                                    Filters.and(Filters.eq("caller", partnerUsername), Filters.eq("receiver", me.username))
                            ))
                            .first();

                    if (doc == null || "ended".equals(doc.getString("status")) || "rejected".equals(doc.getString("status"))) {
                        String reason = doc != null ? doc.getString("status") : "ended";
                        Platform.runLater(() -> performCallEnd(reason));
                        break;
                    }

                    if (isCaller && !callAnswered && doc != null && "accepted".equals(doc.getString("status"))) {
                        callAnswered = true;
                        if (callStartMs == 0) {
                            callStartMs = System.currentTimeMillis();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }, "call-status-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    @FXML
    public void onAnswer(ActionEvent event) {
        callAnswered = true;
        callStartMs = System.currentTimeMillis();

        UserProfile me = Session.getProfile();
        if (me == null) {
            return;
        }

        new Thread(() -> MessageService.updateCallStatus(partnerUsername, me.username, "accepted"), "accept-call").start();

        incomingActions.setVisible(false);
        incomingActions.setManaged(false);
        inCallActions.setVisible(true);
        inCallActions.setManaged(true);
        updateCallStatusText("Connecting...");
        startMedia();
    }

    @FXML
    public void onReject(ActionEvent event) {
        performCallEnd("rejected");
    }

    @FXML
    public void onEndCall(ActionEvent event) {
        performCallEnd("ended");
    }

    private synchronized void performCallEnd(String reason) {
        if (isCallEnded) {
            return;
        }
        isCallEnded = true;
        isWindowOpen = false;

        new Thread(() -> {
            UserProfile me = Session.getProfile();
            if (me != null && partnerUsername != null) {
                MessageService.updateCallStatus(me.username, partnerUsername, reason);
                MessageService.updateCallStatus(partnerUsername, me.username, reason);
            }
        }, "end-call").start();

        stopCallTimer();
        stopRingingPulse();
        stopMedia(reason);

        Platform.runLater(() -> {
            if (endBtn != null && endBtn.getScene() != null) {
                Stage stage = (Stage) endBtn.getScene().getWindow();
                if (stage != null) {
                    stage.close();
                }
            }
        });
    }

    @FXML
    public void onToggleMic(ActionEvent event) {
        boolean muted = micBtn.isSelected();
        P2PAudioService.setMicEnabled(!muted);
    }

    @FXML
    public void onToggleCam(ActionEvent event) {
        boolean off = camBtn.isSelected();
        P2PVideoService.setVideoEnabled(!off);
    }

    private void startMedia() {
        if (inCall) {
            return;
        }
        boolean isVideo = "video".equalsIgnoreCase(callType);

        if (isCaller) {
            P2PAudioService.startServer(this::markAudioConnected);
        } else {
            P2PAudioService.startClient(callerIp, this::markAudioConnected);
        }

        if (isVideo) {
            if (isCaller) {
                P2PVideoService.startServer(remoteVideo, this::markVideoConnected);
            } else {
                P2PVideoService.startClient(callerIp, remoteVideo, this::markVideoConnected);
            }
            syncCameraAvailabilityAsync();
        }

        inCall = true;
        updateCallStatusText(isCaller ? "Ringing" : "Connecting...");

        if (isVideo && !localHasVideo) {
            camBtn.setSelected(true);
            camBtn.setDisable(true);
        }
        if (!isVideo && !localHasMic) {
            micBtn.setSelected(true);
            micBtn.setDisable(true);
        }
    }

    private void stopMedia(String reason) {
        inCall = false;
        P2PVideoService.stopCall();
        P2PAudioService.stop();

        UserProfile me = Session.getProfile();
        if (me != null && partnerUsername != null && isCaller) {
            long duration = (callStartMs > 0) ? System.currentTimeMillis() - callStartMs : 0;
            boolean missed = !callAnswered || "rejected".equals(reason) || duration < 1000;
            MessageService.logCall(me.username, partnerUsername, callType, missed ? 0 : duration, missed ? "missed" : me.username);
        }

        callStartMs = 0;
    }

    private void syncCameraAvailabilityAsync() {
        Thread watcher = new Thread(() -> {
            for (int i = 0; i < 6; i++) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    return;
                }

                if (!isWindowOpen || !"video".equalsIgnoreCase(callType)) {
                    return;
                }

                if (P2PVideoService.isLocalCameraAvailable()) {
                    return;
                }
            }

            if (!P2PVideoService.isLocalCameraAvailable()) {
                localHasVideo = false;
                Platform.runLater(() -> {
                    camBtn.setSelected(true);
                    camBtn.setDisable(true);
                });
            }
        }, "camera-availability-watch");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void loadAvatar(String username) {
        Image fallback = getFallbackAvatarImage();
        if (fallback != null) {
            avatarImage.setImage(fallback);
        }

        if (username == null || username.isBlank()) {
            return;
        }

        String cachedProfilePic = USER_PROFILE_PIC_CACHE.get(username);
        if (cachedProfilePic != null && !cachedProfilePic.isBlank()) {
            Image cached = getCachedAvatarImage(cachedProfilePic);
            if (cached != null) {
                avatarImage.setImage(cached);
            }
        }

        Thread thread = new Thread(() -> {
            try {
                Document doc = UserService.getUserByUsername(username);
                if (doc == null) {
                    return;
                }

                String profilePic = doc.getString("profilePic");
                if (profilePic == null || profilePic.isBlank()) {
                    return;
                }
                USER_PROFILE_PIC_CACHE.put(username, profilePic);

                Image resolved = getCachedAvatarImage(profilePic);
                if (resolved == null) {
                    return;
                }
                Platform.runLater(() -> {
                    if (!isWindowOpen || partnerUsername == null || !partnerUsername.equals(username)) {
                        return;
                    }
                    avatarImage.setImage(resolved);
                });
            } catch (Exception ignored) {
            }
        }, "call-avatar-load-" + username);
        thread.setDaemon(true);
        thread.start();
    }

    private Image getCachedAvatarImage(String rawUrl) {
        String displayableUrl = ImgBbService.toDisplayableUrl(rawUrl);
        if (displayableUrl == null) {
            return null;
        }
        String key = displayableUrl.trim();
        if (key.isEmpty() || !key.startsWith("http")) {
            return null;
        }

        return AVATAR_IMAGE_CACHE.compute(key, (k, existing) -> {
            if (existing != null && !existing.isError()) {
                return existing;
            }
            Image fresh = new Image(k, true);
            fresh.errorProperty().addListener((obs, oldVal, hasError) -> {
                if (Boolean.TRUE.equals(hasError)) {
                    AVATAR_IMAGE_CACHE.remove(k, fresh);
                }
            });
            return fresh;
        });
    }

    private Image getFallbackAvatarImage() {
        Image cached = fallbackAvatarImage;
        if (cached != null) {
            return cached;
        }
        synchronized (CallController.class) {
            if (fallbackAvatarImage != null) {
                return fallbackAvatarImage;
            }
            try {
                fallbackAvatarImage = new Image(MainApp.class.getResourceAsStream("/assets/default_avatar.png"));
            } catch (Exception ignored) {
            }
            if (fallbackAvatarImage == null) {
                try {
                    fallbackAvatarImage = new Image(MainApp.class.getResourceAsStream("/assets/logo_alt.png"));
                } catch (Exception ignored) {
                }
            }
            return fallbackAvatarImage;
        }
    }

    private void applyAvatarClip() {
        if (avatarImage == null) {
            return;
        }
        double radius = Math.max(avatarImage.getFitWidth(), avatarImage.getFitHeight()) / 2.0;
        avatarImage.setClip(new Circle(radius, radius, radius));
    }

    private void startRingingPulse() {
        if (avatarBox == null || ringingPulseTimeline != null) {
            return;
        }
        avatarBox.setScaleX(1.0);
        avatarBox.setScaleY(1.0);
        ringingPulseTimeline = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(avatarBox.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(avatarBox.scaleYProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(avatarBox.opacityProperty(), 0.94, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(
                        Duration.millis(780),
                        new KeyValue(avatarBox.scaleXProperty(), 1.035, Interpolator.EASE_BOTH),
                        new KeyValue(avatarBox.scaleYProperty(), 1.035, Interpolator.EASE_BOTH),
                        new KeyValue(avatarBox.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
                )
        );
        ringingPulseTimeline.setAutoReverse(true);
        ringingPulseTimeline.setCycleCount(Timeline.INDEFINITE);
        ringingPulseTimeline.play();
    }

    private void stopRingingPulse() {
        if (ringingPulseTimeline != null) {
            ringingPulseTimeline.stop();
            ringingPulseTimeline = null;
        }
        if (avatarBox != null) {
            avatarBox.setScaleX(1.0);
            avatarBox.setScaleY(1.0);
            avatarBox.setOpacity(1.0);
        }
    }

    private void markAudioConnected() {
        if (!callAnswered) {
            callAnswered = true;
            callStartMs = System.currentTimeMillis();
        }
        Platform.runLater(() -> {
            updateCallStatusText("Connected");
            startCallTimer();
            stopRingingPulse();
            if (!"video".equalsIgnoreCase(callType)) {
                avatarBox.setVisible(true);
                avatarBox.setManaged(true);
            }
        });
    }

    private void markVideoConnected() {
        if (!callAnswered) {
            callAnswered = true;
            callStartMs = System.currentTimeMillis();
        }
        Platform.runLater(() -> {
            updateCallStatusText("Connected");
            startCallTimer();
            stopRingingPulse();
            remoteVideo.setVisible(true);
            remoteVideo.setManaged(true);
            avatarBox.setVisible(false);
            avatarBox.setManaged(false);
        });
    }

    private void updateCallStatusText(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
        if (callStateHint != null) {
            callStateHint.setText(status);
        }
    }

    private void startCallTimer() {
        if (callTimerLabel == null || callTimerTimeline != null) {
            return;
        }
        if (callStartMs <= 0) {
            callStartMs = System.currentTimeMillis();
        }
        callTimerLabel.setVisible(true);
        callTimerLabel.setManaged(true);
        callTimerTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, e -> updateTimerText()),
                new KeyFrame(Duration.seconds(1))
        );
        callTimerTimeline.setCycleCount(Timeline.INDEFINITE);
        callTimerTimeline.play();
    }

    private void stopCallTimer() {
        if (callTimerTimeline != null) {
            callTimerTimeline.stop();
            callTimerTimeline = null;
        }
        if (callTimerLabel != null) {
            callTimerLabel.setText("00:00");
            callTimerLabel.setVisible(false);
            callTimerLabel.setManaged(false);
        }
    }

    private void updateTimerText() {
        if (callTimerLabel == null || callStartMs <= 0) {
            return;
        }
        long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - callStartMs) / 1000);
        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;
        if (hours > 0) {
            callTimerLabel.setText(String.format("%d:%02d:%02d", hours, minutes, seconds));
        } else {
            callTimerLabel.setText(String.format("%02d:%02d", minutes, seconds));
        }
    }
}
