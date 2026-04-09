package com.lanmessenger;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ForgotPasswordController {

    private enum Step { EMAIL, CODE, RESET }
    private Step current = Step.EMAIL;

    @FXML private ScrollPane root;

    @FXML private ImageView logoImage;
    @FXML private ImageView sadImage;

    @FXML private VBox stepEmail;
    @FXML private VBox stepCode;
    @FXML private VBox stepReset;

    @FXML private TextField emailField;
    @FXML private TextField codeField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;

    @FXML private Button sendCodeBtn;
    @FXML private Button verifyCodeBtn;
    @FXML private Button resetBtn;

    @FXML private Label statusEmail;
    @FXML private Label statusCode;
    @FXML private Label statusReset;

    private SequentialTransition noticeAnim;

    private final FirebaseAuthService auth = new FirebaseAuthService();

    @FXML
    private void onBackgroundClick(MouseEvent e) {
        if (root != null) root.requestFocus();
    }

    @FXML
    public void initialize() {
        javafx.application.Platform.runLater(() -> {
            if (root != null) root.requestFocus();
        });
        SmoothScrollUtil.apply(root);

        if (logoImage != null) {
            try (var in = getClass().getResourceAsStream("/assets/logo.png")) {
                if (in != null) logoImage.setImage(new Image(in));
            } catch (Exception ignored) {}
        }
        if (sadImage != null) {
            try (var in = getClass().getResourceAsStream("/assets/sad.png")) {
                if (in != null) sadImage.setImage(new Image(in));
            } catch (Exception ignored) {}
        }

        if (emailField != null) {
            emailField.setTextFormatter(new TextFormatter<>(change -> {
                if (change.getText() != null) change.setText(change.getText().toLowerCase());
                return change;
            }));
        }

        addEnter(emailField, () -> { if (sendCodeBtn != null) sendCodeBtn.fire(); });

        setStepVisible(stepCode, false);
        setStepVisible(stepReset, false);

        if (verifyCodeBtn != null) {
            verifyCodeBtn.setDisable(true);
            verifyCodeBtn.setVisible(false);
            verifyCodeBtn.setManaged(false);
        }
        if (resetBtn != null) {
            resetBtn.setDisable(true);
            resetBtn.setVisible(false);
            resetBtn.setManaged(false);
        }

        if (sendCodeBtn != null) {
            sendCodeBtn.setText("Send Mail");
        }

        showStep(Step.EMAIL);
    }

    private void addEnter(Control c, Runnable action) {
        if (c == null) return;
        c.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                action.run();
                e.consume();
            }
        });
    }

    private boolean isValidEmail(String s) {
        return s != null && s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private void showStep(Step step) {
        current = step;

        setStepVisible(stepEmail, step == Step.EMAIL);
        setStepVisible(stepCode, false);
        setStepVisible(stepReset, false);

        clearStatus(statusEmail);
        clearStatus(statusCode);
        clearStatus(statusReset);

        javafx.application.Platform.runLater(() -> {
            if (emailField != null) emailField.requestFocus();
        });
    }

    private void setStepVisible(VBox box, boolean on) {
        if (box == null) return;
        box.setVisible(on);
        box.setManaged(on);
    }

    private Label activeStatusLabel() {
        return switch (current) {
            case EMAIL -> statusEmail;
            case CODE -> statusCode;
            case RESET -> statusReset;
        };
    }

    private void clearStatus(Label l) {
        if (l == null) return;
        l.setText("");
        l.setStyle("");
        l.setVisible(false);
        l.setManaged(false);
        l.setOpacity(1);
    }

    private void showMessage(String msg, boolean good, boolean autoHide) {
        Label target = activeStatusLabel();
        if (target == null) return;

        if (noticeAnim != null) noticeAnim.stop();

        target.setText(msg);
        target.setStyle(good ? "-fx-text-fill:#16A34A;" : "-fx-text-fill:#DC2626;");
        target.setVisible(true);
        target.setManaged(true);
        target.setOpacity(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), target);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setInterpolator(Interpolator.EASE_BOTH);

        if (!autoHide) {
            noticeAnim = new SequentialTransition(fadeIn);
            noticeAnim.play();
            return;
        }

        PauseTransition stay = new PauseTransition(Duration.millis(1400));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), target);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);

        noticeAnim = new SequentialTransition(fadeIn, stay, fadeOut);
        noticeAnim.setOnFinished(e -> clearStatus(target));
        noticeAnim.play();
    }

    private void setBusy(boolean busy) {
        if (sendCodeBtn != null) sendCodeBtn.setDisable(busy);
        if (emailField != null) emailField.setDisable(busy);
    }

    @FXML
    private void onSendCode(ActionEvent e) {
        String email = emailField == null ? "" : emailField.getText().trim();
        if (!isValidEmail(email)) {
            showMessage("Please enter a valid email.", false, false);
            return;
        }

        setBusy(true);
        showMessage("Sending password reset email...", true, false);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                FirebaseAuthService.AuthResult r = auth.sendPasswordResetEmail(email);
                if (!r.ok) {
                    updateMessage(r.message);
                    return false;
                }
                updateMessage("If this email is registered, a reset link has been sent. Please check your inbox and spam folder.");
                return true;
            }
        };

        task.setOnSucceeded(ev -> {
            setBusy(false);

            if (task.getValue()) {
                showMessage(task.getMessage(), true, false);

                PauseTransition back = new PauseTransition(Duration.seconds(6));
                back.setOnFinished(x -> swapRootWithFade("/login.fxml", "/login.css"));
                back.play();
            } else {
                showMessage(task.getMessage(), false, false);
            }
        });

        task.setOnFailed(ev -> {
            setBusy(false);
            showMessage("Network/Server error. Try again.", false, false);
        });

        new Thread(task, "firebase-reset-mail").start();
    }

    @FXML private void onVerifyCode(ActionEvent e) {}
    @FXML private void onResendCode(ActionEvent e) { onSendCode(e); }
    @FXML private void onBackToEmail(ActionEvent e) { showStep(Step.EMAIL); }
    @FXML private void onResetPassword(ActionEvent e) {}
    @FXML private void onBackToCode(ActionEvent e) { showStep(Step.EMAIL); }

    @FXML
    private void onBackToLogin(ActionEvent e) {
        try {
            SceneNavigator.swapRootWithFade(e, root, "/login.fxml", "/login.css");
        } catch (Exception ex) {
            ex.printStackTrace();
            showMessage("Could not open the page.", false, false);
        }
    }

    @FXML
    private void onOpenFeatures(ActionEvent e) {
        swapRootWithFade("/features.fxml", "/info_pages.css");
    }

    @FXML
    private void onOpenPrivacySafety(ActionEvent e) {
        swapRootWithFade("/privacy_safety.fxml", "/info_pages.css");
    }

    @FXML
    private void onOpenDevelopers(ActionEvent e) {
        swapRootWithFade("/developers.fxml", "/info_pages.css");
    }

    @FXML
    private void onOpenHelpCenter(ActionEvent e) {
        swapRootWithFade("/help_center.fxml", "/info_pages.css");
    }

    private void swapRootWithFade(String fxmlPath, String cssPath) {
        try {
            Stage stage = (Stage) root.getScene().getWindow();
            SceneNavigator.swapRootWithFade(stage.getScene(), fxmlPath, cssPath);
        } catch (Exception ex) {
            ex.printStackTrace();
            showMessage("Could not open the page.", false, false);
        }
    }
}
