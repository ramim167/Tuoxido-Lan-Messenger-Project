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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.geometry.Side;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LoginController {

    @FXML
    private ScrollPane root;

    @FXML
    private ImageView logoImage;
    @FXML
    private ImageView funImage;

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordVisibleField;

    @FXML
    private Button togglePassBtn;
    @FXML
    private FontIcon eyeIcon;

    @FXML
    private CheckBox keepSignedIn;

    @FXML
    private Label statusLabel;

    @FXML
    private Button loginBtn;
    @FXML
    private Button resendVerifyBtn;

    private SequentialTransition noticeAnim;
    private boolean passwordVisible = false;
    private final ContextMenu emailSuggestionsMenu = new ContextMenu();
    private List<String> rememberedEmailSuggestions = new ArrayList<>();

    private final FirebaseAuthService auth = new FirebaseAuthService();

    @FXML
    private void onBackgroundClick(MouseEvent e) {
        emailSuggestionsMenu.hide();
        root.requestFocus();
    }

    @FXML
    public void initialize() {
        javafx.application.Platform.runLater(() -> root.requestFocus());
        SmoothScrollUtil.apply(root);

        // Restore the saved local session before showing the login form.
        String savedEmail = Session.getSavedEmail();
        String savedLocalId = Session.getSavedLocalId();

        if (savedEmail != null && !savedEmail.isEmpty() && savedLocalId != null && !savedLocalId.isEmpty()) {
            setBusy(true);
            showNotice("Auto-signing in...", true);

            Task<Boolean> autoLoginTask = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    UserProfile profile = UserService.loadRequiredProfile(savedLocalId, savedEmail);
                    if (profile == null) {
                        return false;
                    }

                    Session.setAutoLoggedIn(true);
                    Session.setProfile(profile);
                    return true;
                }
            };

            autoLoginTask.setOnSucceeded(e -> {
                if (autoLoginTask.getValue()) {
                    openMain();
                } else {
                    Session.clear();
                    setBusy(false);
                    showNotice("Saved session is invalid. Please login again.", false);
                }
            });

            autoLoginTask.setOnFailed(e -> {
                Session.clear();
                setBusy(false);
                showNotice("Auto-login failed. Please login manually.", false);
            });

            new Thread(autoLoginTask, "auto-login").start();
        }

        setImageOrHide(logoImage, "/assets/logo.png");
        setImageOrHide(funImage, "/assets/fun.png");

        if (funImage != null) {
            funImage.setFitWidth(350);
            funImage.setSmooth(true);
        }

        emailField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                loginBtn.fire();
                e.consume();
            }
        });

        passwordField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                loginBtn.fire();
                e.consume();
            }
        });

        passwordVisibleField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                loginBtn.fire();
                e.consume();
            }
        });

        emailField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getText() != null) {
                change.setText(change.getText().toLowerCase());
            }
            return change;
        }));
        setupEmailSuggestions(savedEmail);

        passwordField.textProperty().addListener((obs, oldVal, newVal) -> updateEyeVisibility(newVal));
        passwordVisibleField.textProperty().addListener((obs, oldVal, newVal) -> updateEyeVisibility(newVal));

        loginBtn.setOnMouseEntered(ev -> setScale(loginBtn, 1.02));
        loginBtn.setOnMouseExited(ev -> setScale(loginBtn, 1.00));
        loginBtn.setOnMousePressed(ev -> setScale(loginBtn, 0.98));
        loginBtn.setOnMouseReleased(ev -> setScale(loginBtn, 1.02));

        togglePassBtn.setVisible(false);
        togglePassBtn.setManaged(false);

        if (eyeIcon != null) {
            eyeIcon.setIconLiteral("mdi2e-eye");
        }
    }

    private void setupEmailSuggestions(String savedEmail) {
        if (!emailSuggestionsMenu.getStyleClass().contains("email-suggestions-menu")) {
            emailSuggestionsMenu.getStyleClass().add("email-suggestions-menu");
        }

        rememberedEmailSuggestions = new ArrayList<>(Session.getSavedEmailSuggestions());
        if (savedEmail != null && !savedEmail.isBlank()) {
            String normalized = savedEmail.trim().toLowerCase();
            rememberedEmailSuggestions.removeIf(it -> normalized.equalsIgnoreCase(it));
            rememberedEmailSuggestions.add(0, normalized);
        }

        emailField.focusedProperty().addListener((obs, oldFocused, focused) -> {
            if (!focused) {
                emailSuggestionsMenu.hide();
            }
        });

        emailField.textProperty().addListener((obs, oldText, newText) -> showEmailSuggestions());
    }

    private void showEmailSuggestions() {
        if (!emailField.isFocused()) {
            emailSuggestionsMenu.hide();
            return;
        }

        String input = emailField.getText() == null ? "" : emailField.getText().trim().toLowerCase();
        List<String> filtered = rememberedEmailSuggestions.stream()
                .filter(email -> input.isEmpty() || email.startsWith(input))
                .filter(email -> !email.equalsIgnoreCase(input))
                .limit(6)
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            emailSuggestionsMenu.hide();
            return;
        }

        List<MenuItem> items = new ArrayList<>();
        for (String email : filtered) {
            MenuItem item = new MenuItem(email);
            item.getStyleClass().add("email-suggestion-item");
            item.setOnAction(e -> {
                emailField.setText(email);
                emailField.positionCaret(email.length());
                emailSuggestionsMenu.hide();
                if (passwordField != null && passwordField.isVisible()) {
                    passwordField.requestFocus();
                } else if (passwordVisibleField != null) {
                    passwordVisibleField.requestFocus();
                }
            });
            items.add(item);
        }
        emailSuggestionsMenu.getItems().setAll(items);

        if (!emailSuggestionsMenu.isShowing()) {
            emailSuggestionsMenu.show(emailField, Side.BOTTOM, 0, 4);
        }

        if (emailSuggestionsMenu.getScene() != null && emailField.getScene() != null) {
            emailSuggestionsMenu.getScene().getStylesheets().setAll(emailField.getScene().getStylesheets());
            if (!emailSuggestionsMenu.getScene().getRoot().getStyleClass().contains("root")) {
                emailSuggestionsMenu.getScene().getRoot().getStyleClass().add("root");
            }
        }
    }

    private void updateEyeVisibility(String text) {
        boolean hasText = text != null && !text.isEmpty();
        togglePassBtn.setVisible(hasText);
        togglePassBtn.setManaged(hasText);
    }

    private void setScale(Button btn, double scale) {
        btn.setScaleX(scale);
        btn.setScaleY(scale);
    }

    private void setBusy(boolean busy) {
        loginBtn.setDisable(busy);
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        passwordVisibleField.setDisable(busy);
        togglePassBtn.setDisable(busy);

        if (keepSignedIn != null) {
            keepSignedIn.setDisable(busy);
        }
    }

    private void flashLoginBtn(String colorHex) {
        loginBtn.setStyle(
                "-fx-background-color: " + colorHex + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 10;"
        );

        PauseTransition pt = new PauseTransition(Duration.millis(260));
        pt.setOnFinished(e -> loginBtn.setStyle(""));
        pt.play();
    }

    private boolean isValidEmail(String s) {
        return s != null && s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private void showNotice(String msg, boolean good) {
        if (statusLabel == null) return;

        if (noticeAnim != null) {
            noticeAnim.stop();
        }

        statusLabel.setText(msg);

        if (good) {
            statusLabel.setStyle("-fx-text-fill:#16A34A; -fx-font-size: 12.5px; -fx-font-weight: 600;");
        } else {
            statusLabel.setStyle("-fx-text-fill:#DC2626; -fx-font-size: 12.5px; -fx-font-weight: 500;");
        }

        statusLabel.setOpacity(0);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(420), statusLabel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition stay = new PauseTransition(Duration.millis(1500));

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

    private void setImageOrHide(ImageView view, String resourcePath) {
        if (view == null) return;

        try (InputStream in = MainApp.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                view.setVisible(false);
                view.setManaged(false);
                return;
            }
            view.setImage(new Image(in));
        } catch (Exception e) {
            view.setVisible(false);
            view.setManaged(false);
        }
    }

    @FXML
    private void onForgotPassword(ActionEvent e) {
        swapRootWithFade("/forgot_password.fxml", "/forgot_password.css");
    }

    @FXML
    private void onGoToSignUp(ActionEvent e) {
        swapRootWithFade("/signup.fxml", "/signup.css");
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
            showNotice("Could not open page.", false);
        }
    }

    @FXML
    private void onLogin(ActionEvent e) {
        String email = emailField.getText().trim();
        String pass = passwordVisible ? passwordVisibleField.getText() : passwordField.getText();

        boolean ok = !email.isEmpty() && !pass.isEmpty() && isValidEmail(email);

        if (!ok) {
            showNotice("Please enter a valid email and password.", false);
            flashLoginBtn("#B91C1C");
            return;
        }

        setBusy(true);
        showNotice("Signing in...", true);
        flashLoginBtn("#2563EB");

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                FirebaseAuthService.AuthResult r = auth.signIn(email, pass);

                if (!r.ok) {
                    updateMessage(r.message);
                    return false;
                }

                boolean verified = auth.isEmailVerified(r.idToken);
                if (!verified) {
                    auth.sendEmailVerification(r.idToken);
                    updateMessage("Email not verified. Verification mail sent. Please verify then login.");
                    return false;
                }

                updateMessage("OK");

                Session.setAuth(r);
                Session.rememberEmailSuggestion(r.email);
                // If the checkbox is not present in the current UI, default to remembering
                // the authenticated user so app restart does not require another login.
                boolean shouldRemember = keepSignedIn == null || keepSignedIn.isSelected();
                if (shouldRemember) {
                    Session.rememberAuth(r);
                } else {
                    Session.clearRememberedAuth();
                }

                UserProfile profile = UserService.loadRequiredProfile(r.localId, r.email);
                if (profile == null) {
                    updateMessage("Account found, but no matching MongoDB profile was loaded.");
                    Session.clear();
                    return false;
                }

                Session.setProfile(profile);
                return true;
            }
        };

        task.setOnSucceeded(ev -> {
            setBusy(false);

            if (task.getValue()) {
                flashLoginBtn("#16A34A");
                PauseTransition goNext = new PauseTransition(Duration.millis(180));
                goNext.setOnFinished(x -> openMain());
                goNext.play();
            } else {
                showNotice(task.getMessage(), false);
                flashLoginBtn("#B91C1C");
            }
        });

        task.setOnFailed(ev -> {
            setBusy(false);
            showNotice("Network/Server error. Try again.", false);
            flashLoginBtn("#B91C1C");
        });

        new Thread(task, "firebase-login").start();
    }

    private void openMain() {
        try {
            Stage stage = (Stage) emailField.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/main.fxml"));
            Scene mainScene = new Scene(loader.load());

            ThemeManager.applyMainTheme(mainScene);
            stage.setScene(mainScene);
        } catch (Exception ex) {
            ex.printStackTrace();
            showNotice("Could not load main UI.", false);
            flashLoginBtn("#B91C1C");
        }
    }

    @FXML
    private void togglePasswordVisibility() {
        if (passwordVisible) {
            passwordField.setText(passwordVisibleField.getText());

            passwordField.setVisible(true);
            passwordField.setManaged(true);

            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);

            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
        } else {
            passwordVisibleField.setText(passwordField.getText());

            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);

            passwordField.setVisible(false);
            passwordField.setManaged(false);

            passwordVisibleField.requestFocus();
            passwordVisibleField.positionCaret(passwordVisibleField.getText().length());
        }

        passwordVisible = !passwordVisible;

        if (eyeIcon != null) {
            eyeIcon.setIconLiteral(passwordVisible ? "mdi2e-eye-off" : "mdi2e-eye");
        }
    }
}
