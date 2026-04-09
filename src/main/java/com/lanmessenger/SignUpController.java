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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

public class SignUpController {

    @FXML
    private ScrollPane root;

    @FXML
    private TextField nameField;
    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private ImageView logoImage;
    @FXML
    private Label statusLabel;
    @FXML
    private Button createBtn;

    @FXML
    private ImageView happyImage;

    private SequentialTransition noticeAnim;

    private final FirebaseAuthService auth = new FirebaseAuthService();

    @FXML
    private void onBackgroundClick(MouseEvent e) {
        root.requestFocus();
    }

    @FXML
    public void initialize() {
        javafx.application.Platform.runLater(() -> root.requestFocus());
        SmoothScrollUtil.apply(root);

        nameField.addEventFilter(KeyEvent.KEY_PRESSED, this::enterToCreate);
        emailField.addEventFilter(KeyEvent.KEY_PRESSED, this::enterToCreate);
        passwordField.addEventFilter(KeyEvent.KEY_PRESSED, this::enterToCreate);
        confirmPasswordField.addEventFilter(KeyEvent.KEY_PRESSED, this::enterToCreate);

        if (happyImage != null) {
            happyImage.setImage(new Image(getClass().getResourceAsStream("/assets/happy.png")));
        }
        if (logoImage != null) {
            logoImage.setImage(new Image(getClass().getResourceAsStream("/assets/logo.png")));
        }

        emailField.setTextFormatter(new javafx.scene.control.TextFormatter<>(change -> {
            if (change.getText() != null) change.setText(change.getText().toLowerCase());
            return change;
        }));

        createBtn.setOnMouseEntered(ev -> {
            createBtn.setScaleX(1.02);
            createBtn.setScaleY(1.02);
        });
        createBtn.setOnMouseExited(ev -> {
            createBtn.setScaleX(1.0);
            createBtn.setScaleY(1.0);
        });
        createBtn.setOnMousePressed(ev -> {
            createBtn.setScaleX(0.98);
            createBtn.setScaleY(0.98);
        });
        createBtn.setOnMouseReleased(ev -> {
            createBtn.setScaleX(1.02);
            createBtn.setScaleY(1.02);
        });
    }

    private void enterToCreate(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) {
            createBtn.fire();
            e.consume();
        }
    }

    private boolean isValidEmail(String s) {
        return s != null && s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private void showNotice(String msg, boolean good) {
        showNotice(msg, good, 1600);
    }

    private void showNotice(String msg, boolean good, int stayMillis) {
        if (statusLabel == null) return;

        if (noticeAnim != null) noticeAnim.stop();

        statusLabel.setText(msg);
        statusLabel.setStyle(good ? "-fx-text-fill:#16A34A;" : "");
        statusLabel.setOpacity(0);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(420), statusLabel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition stay = new PauseTransition(Duration.millis(Math.max(0, stayMillis)));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(420), statusLabel);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        noticeAnim = new SequentialTransition(fadeIn, stay, fadeOut);
        noticeAnim.setOnFinished(e -> {
            statusLabel.setText("");
            statusLabel.setStyle("");
        });
        noticeAnim.play();
    }

    private void setBusy(boolean busy) {
        createBtn.setDisable(busy);
        nameField.setDisable(busy);
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        confirmPasswordField.setDisable(busy);
    }

    @FXML
    private void onCreateAccount(ActionEvent e) {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String pass = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        if (name.isEmpty()) {
            showNotice("Please enter your name.", false);
            return;
        }
        if (!isValidEmail(email)) {
            showNotice("Please enter a valid email.", false);
            return;
        }
        if (pass == null || pass.length() < 6) {
            showNotice("Password must be at least 6 characters.", false);
            return;
        }
        if (!pass.equals(confirm)) {
            showNotice("Passwords do not match.", false);
            return;
        }

        setBusy(true);
        showNotice("Creating account...", true);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {

                FirebaseAuthService.AuthResult r = auth.signUp(email, pass);
                if (!r.ok) {
                    updateMessage(r.message);
                    return false;
                }

                String generatedUsername = UserService.registerUser(name, email, pass);
                if (generatedUsername == null || generatedUsername.isBlank()) {
                    updateMessage("Account created in Firebase, but profile setup in MongoDB failed.");
                    return false;
                }

                UserProfileStore.saveIdentity(r.localId, name, r.email);
                UserProfileStore.updateUsername(r.localId, generatedUsername);

                FirebaseAuthService.AuthResult v = auth.sendEmailVerification(r.idToken);

                if (!v.ok) {
                    updateMessage(v.message);
                    return false;
                }

                updateMessage("Account created. A verification email has been sent. Please verify it, then log in.");
                return true;
            }
        };

        task.setOnSucceeded(ev -> {
            setBusy(false);
            if (task.getValue()) {
                showNotice(task.getMessage(), true, 3600);

                PauseTransition goBack = new PauseTransition(Duration.millis(3500));
                goBack.setOnFinished(x -> swapRootWithFade("/login.fxml", "/login.css"));
                goBack.play();
            } else {
                showNotice(task.getMessage(), false);
            }
        });

        task.setOnFailed(ev -> {
            setBusy(false);
            showNotice("Network/Server error. Try again.", false);
        });

        new Thread(task, "firebase-signup").start();
    }

    @FXML
    private void onBackToLogin(ActionEvent e) {
        try {
            SceneNavigator.swapRootWithFade(e, root, "/login.fxml", "/login.css");
        } catch (Exception ex) {
            ex.printStackTrace();
            showNotice("Could not open Login.", false);
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
            showNotice("Could not open Login.", false);
        }
    }
}
