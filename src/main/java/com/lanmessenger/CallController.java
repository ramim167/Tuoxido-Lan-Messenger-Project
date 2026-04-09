package com.lanmessenger;

import com.mongodb.client.model.Filters;
import javafx.animation.KeyFrame;
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

        incomingActions.setVisible(!caller);
        incomingActions.setManaged(!caller);
        inCallActions.setVisible(caller);
        inCallActions.setManaged(caller);
        if (callTimerLabel != null) {
            callTimerLabel.setVisible(false);
            callTimerLabel.setManaged(false);
            callTimerLabel.setText("00:00");
        }

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
        try {
            Document doc = UserService.getUserByUsername(username);
            if (doc != null) {
                String pic = doc.getString("profilePic");
                if (pic != null && pic.startsWith("http")) {
                    avatarImage.setImage(new Image(pic, true));
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            avatarImage.setImage(new Image(MainApp.class.getResourceAsStream("/assets/logo_alt.png")));
        } catch (Exception ignored) {
        }
    }

    private void applyAvatarClip() {
        if (avatarImage == null) {
            return;
        }
        double radius = Math.max(avatarImage.getFitWidth(), avatarImage.getFitHeight()) / 2.0;
        avatarImage.setClip(new Circle(radius, radius, radius));
    }

    private void markAudioConnected() {
        if (!callAnswered) {
            callAnswered = true;
            callStartMs = System.currentTimeMillis();
        }
        Platform.runLater(() -> {
            updateCallStatusText("Connected");
            startCallTimer();
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
