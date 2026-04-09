package com.lanmessenger;

import javafx.animation.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MainApp extends Application {
    public enum Theme { LIGHT, DARK }
    public static Theme currentTheme = Theme.LIGHT;

    private static final double DEFAULT_W = 1250;
    private static final double DEFAULT_H = 750;

    private static final double MIN_W = 900;
    private static final double MIN_H = 600;

    private static final double LOGO1_W = 220;
    private static final double LOGO2_W = 220;

    @Override
    public void start(Stage stage) {

        MongoDatabaseService.connect();

        StackPane splashRoot = new StackPane();
        Scene scene = new Scene(splashRoot, DEFAULT_W, DEFAULT_H);

        ImageView bgView = new ImageView(new Image(MainApp.class.getResourceAsStream("/assets/bgT.jpg")));
        bgView.setPreserveRatio(false);
        bgView.fitWidthProperty().bind(scene.widthProperty());
        bgView.fitHeightProperty().bind(scene.heightProperty());
        bgView.setOpacity(0.92);

        Rectangle baseWash = new Rectangle();
        baseWash.widthProperty().bind(scene.widthProperty());
        baseWash.heightProperty().bind(scene.heightProperty());
        baseWash.setFill(new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(245, 245, 245, 0.76)),
                new Stop(0.42, Color.rgb(214, 214, 214, 0.22)),
                new Stop(1, Color.rgb(10, 10, 10, 0.30))
        ));

        Rectangle topFlow = new Rectangle();
        topFlow.widthProperty().bind(scene.widthProperty());
        topFlow.heightProperty().bind(scene.heightProperty());
        topFlow.setFill(new LinearGradient(
                0.15, 0, 0.85, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.68)),
                new Stop(0.35, Color.rgb(230, 230, 230, 0.14)),
                new Stop(1, Color.rgb(0, 0, 0, 0.34))
        ));
        topFlow.setOpacity(0.88);

        Circle glowLeft = new Circle(210);
        glowLeft.setFill(new RadialGradient(
                0, 0, 0.35, 0.35, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.58)),
                new Stop(0.45, Color.rgb(225, 225, 225, 0.20)),
                new Stop(1, Color.rgb(0, 0, 0, 0))
        ));
        glowLeft.setEffect(new GaussianBlur(36));
        glowLeft.setTranslateX(-380);
        glowLeft.setTranslateY(-120);

        Circle glowRight = new Circle(260);
        glowRight.setFill(new RadialGradient(
                0, 0, 0.65, 0.40, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(20, 20, 20, 0.34)),
                new Stop(0.55, Color.rgb(60, 60, 60, 0.12)),
                new Stop(1, Color.rgb(255, 255, 255, 0))
        ));
        glowRight.setEffect(new GaussianBlur(48));
        glowRight.setTranslateX(430);
        glowRight.setTranslateY(150);

        Rectangle sheenBand = new Rectangle(420, 980);
        sheenBand.setArcWidth(420);
        sheenBand.setArcHeight(420);
        sheenBand.setRotate(-22);
        sheenBand.setFill(new LinearGradient(
                0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0)),
                new Stop(0.48, Color.rgb(255, 255, 255, 0.19)),
                new Stop(1, Color.rgb(255, 255, 255, 0))
        ));
        sheenBand.setEffect(new GaussianBlur(30));
        sheenBand.setTranslateX(350);
        sheenBand.setTranslateY(-10);

        Region vignette = new Region();
        vignette.setMouseTransparent(true);
        vignette.prefWidthProperty().bind(scene.widthProperty());
        vignette.prefHeightProperty().bind(scene.heightProperty());
        vignette.setStyle(
                "-fx-background-color: " +
                        "radial-gradient(center 50% 44%, radius 85%, rgba(255,255,255,0.00) 0%, rgba(255,255,255,0.00) 48%, rgba(15,15,15,0.18) 100%);"
        );

        ImageView logo1View = new ImageView(new Image(MainApp.class.getResourceAsStream("/assets/logo.png")));
        logo1View.setPreserveRatio(true);
        logo1View.setFitWidth(LOGO1_W);

        ImageView logo2View = new ImageView(new Image(MainApp.class.getResourceAsStream("/assets/logo2.png")));
        logo2View.setPreserveRatio(true);
        logo2View.setFitWidth(LOGO2_W);
        logo2View.setOpacity(0);
        logo2View.setScaleX(0.92);
        logo2View.setScaleY(0.92);

        splashRoot.getChildren().addAll(
                bgView,
                baseWash,
                topFlow,
                glowLeft,
                glowRight,
                sheenBand,
                vignette,
                logo2View,
                logo1View
        );

        stage.setTitle("TUOXIDO");
        stage.setMinWidth(MIN_W);
        stage.setMinHeight(MIN_H);

        stage.setScene(scene);
        stage.setWidth(DEFAULT_W);
        stage.setHeight(DEFAULT_H);
        stage.setResizable(true);
        stage.show();

        splashRoot.setOpacity(0);
        FadeTransition splashIn = new FadeTransition(Duration.millis(650), splashRoot);
        splashIn.setFromValue(0);
        splashIn.setToValue(1);
        splashIn.setInterpolator(Interpolator.EASE_BOTH);
        splashIn.play();

        TranslateTransition driftLeft = new TranslateTransition(Duration.millis(7200), glowLeft);
        driftLeft.setFromX(-420);
        driftLeft.setToX(-300);
        driftLeft.setFromY(-140);
        driftLeft.setToY(-90);
        driftLeft.setAutoReverse(true);
        driftLeft.setCycleCount(Animation.INDEFINITE);
        driftLeft.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition breatheLeft = new ScaleTransition(Duration.millis(5600), glowLeft);
        breatheLeft.setFromX(0.94);
        breatheLeft.setFromY(0.94);
        breatheLeft.setToX(1.08);
        breatheLeft.setToY(1.08);
        breatheLeft.setAutoReverse(true);
        breatheLeft.setCycleCount(Animation.INDEFINITE);
        breatheLeft.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition driftRight = new TranslateTransition(Duration.millis(7800), glowRight);
        driftRight.setFromX(420);
        driftRight.setToX(300);
        driftRight.setFromY(170);
        driftRight.setToY(110);
        driftRight.setAutoReverse(true);
        driftRight.setCycleCount(Animation.INDEFINITE);
        driftRight.setInterpolator(Interpolator.EASE_BOTH);

        FadeTransition pulseFlow = new FadeTransition(Duration.millis(4600), topFlow);
        pulseFlow.setFromValue(0.72);
        pulseFlow.setToValue(0.94);
        pulseFlow.setAutoReverse(true);
        pulseFlow.setCycleCount(Animation.INDEFINITE);
        pulseFlow.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition sheenSweep = new TranslateTransition(Duration.millis(6200), sheenBand);
        sheenSweep.setFromX(360);
        sheenSweep.setToX(-280);
        sheenSweep.setAutoReverse(true);
        sheenSweep.setCycleCount(Animation.INDEFINITE);
        sheenSweep.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition ambientMotion = new ParallelTransition(
                driftLeft, breatheLeft, driftRight, pulseFlow, sheenSweep
        );
        ambientMotion.play();

        int SHAKE_STEP_MS = 0;
        double SHAKE_POWER = 0;
        int SHAKE_CYCLES = 0;

        double FLOAT_SCALE_TO = 1.10;
        double FLOAT_FADE_TO  = 0.85;
        int FLOAT_TOTAL_MS    = 1500;

        double LOGO1_LEFT_X = -20;
        double LOGO2_START_X = LOGO1_LEFT_X+70;

        double LOGO2_END_OFFSET = 78;

        int SWAP_MS = 650;

        int PAUSE_MS = 300;

        TranslateTransition shake = new TranslateTransition(Duration.millis(SHAKE_STEP_MS), logo1View);
        shake.setFromX(-SHAKE_POWER);
        shake.setToX(SHAKE_POWER);
        shake.setAutoReverse(true);
        shake.setCycleCount(SHAKE_CYCLES);

        ScaleTransition float1 = new ScaleTransition(Duration.millis(FLOAT_TOTAL_MS), logo1View);
        float1.setFromX(1.00); float1.setFromY(1.00);
        float1.setToX(FLOAT_SCALE_TO); float1.setToY(FLOAT_SCALE_TO);
        float1.setAutoReverse(true);
        float1.setCycleCount(2);
        float1.setInterpolator(Interpolator.EASE_BOTH);

        FadeTransition depthFade = new FadeTransition(Duration.millis(FLOAT_TOTAL_MS), logo1View);
        depthFade.setFromValue(1.0);
        depthFade.setToValue(FLOAT_FADE_TO);
        depthFade.setAutoReverse(true);
        depthFade.setCycleCount(2);
        depthFade.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition shakePlus = new ParallelTransition(shake, float1, depthFade);

        TranslateTransition logo1MoveLeft = new TranslateTransition(Duration.millis(SWAP_MS), logo1View);
        logo1MoveLeft.setToX(LOGO1_LEFT_X);
        logo1MoveLeft.setInterpolator(Interpolator.EASE_BOTH);

        PauseTransition alignLogo2Start = new PauseTransition(Duration.millis(1));
        alignLogo2Start.setOnFinished(ev -> {
            logo2View.setTranslateX(LOGO2_START_X);
        });

        double logo2EndX = LOGO1_LEFT_X + LOGO2_END_OFFSET;

        TranslateTransition logo2Slide = new TranslateTransition(Duration.millis(SWAP_MS), logo2View);
        logo2Slide.setToX(logo2EndX);
        logo2Slide.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition logo2FadeIn = new FadeTransition(Duration.millis(SWAP_MS), logo2View);
        logo2FadeIn.setFromValue(0);
        logo2FadeIn.setToValue(1);
        logo2FadeIn.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition logo2Scale = new ScaleTransition(Duration.millis(SWAP_MS), logo2View);
        logo2Scale.setFromX(0.92);
        logo2Scale.setFromY(0.92);
        logo2Scale.setToX(1.0);
        logo2Scale.setToY(1.0);
        logo2Scale.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition swap = new ParallelTransition(
                logo1MoveLeft,
                logo2Slide, logo2FadeIn, logo2Scale
        );

        PauseTransition pause = new PauseTransition(Duration.millis(PAUSE_MS));

        SequentialTransition seq = new SequentialTransition(shakePlus, alignLogo2Start, swap, pause);
        seq.setOnFinished(e -> goToLogin(stage, splashRoot));
        seq.play();
    }

    private void goToLogin(Stage stage, StackPane splashRoot) {

        final double keepW = stage.getWidth();
        final double keepH = stage.getHeight();
        final boolean wasMaximized = stage.isMaximized();

        FadeTransition splashOut = new FadeTransition(Duration.millis(450), splashRoot);
        splashOut.setFromValue(1);
        splashOut.setToValue(0);
        splashOut.setInterpolator(Interpolator.EASE_BOTH);

        splashOut.setOnFinished(ev -> {
            try {
                FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/login.fxml"));
                Parent loginRoot = loader.load();

                loginRoot.setOpacity(0);

                Scene scene = stage.getScene();
                scene.setRoot(loginRoot);

                scene.getStylesheets().clear();
                if (MainApp.class.getResource("/login.css") != null) {
                    scene.getStylesheets().add(MainApp.class.getResource("/login.css").toExternalForm());
                }

                if (wasMaximized) {
                    stage.setMaximized(true);
                } else {
                    stage.setWidth(keepW);
                    stage.setHeight(keepH);
                }

                FadeTransition loginIn = new FadeTransition(Duration.millis(450), loginRoot);
                loginIn.setFromValue(0);
                loginIn.setToValue(1);
                loginIn.setInterpolator(Interpolator.EASE_BOTH);
                loginIn.play();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        splashOut.play();
    }
}
