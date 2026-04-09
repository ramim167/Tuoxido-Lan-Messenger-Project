package com.lanmessenger;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

public final class SmoothScrollUtil {
    private static final String TIMELINE_KEY = "smoothScrollTimeline";
    private static final double SCROLL_FACTOR = 0.0018;
    private static final Duration ANIM_DURATION = Duration.millis(150);
    private static final double LIGHT_SCROLL_FACTOR = 0.00165;
    private static final Duration LIGHT_ANIM_DURATION = Duration.millis(110);

    private SmoothScrollUtil() {}

    public static void apply(ScrollPane scrollPane) {
        install(scrollPane, SCROLL_FACTOR, ANIM_DURATION);
    }

    public static void applyLight(ScrollPane scrollPane) {
        install(scrollPane, LIGHT_SCROLL_FACTOR, LIGHT_ANIM_DURATION);
    }

    private static void install(ScrollPane scrollPane, double scrollFactor, Duration animDuration) {
        if (scrollPane == null) return;
        if (Boolean.TRUE.equals(scrollPane.getProperties().get("smoothScrollInstalled"))) return;

        scrollPane.getProperties().put("smoothScrollInstalled", true);
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0) return;

            double delta = -event.getDeltaY() * scrollFactor;
            Object existing = scrollPane.getProperties().get(TIMELINE_KEY);
            double currentTarget = scrollPane.getVvalue();
            if (existing instanceof Timeline timeline) {
                timeline.stop();
                Object storedTarget = scrollPane.getProperties().get("smoothScrollTarget");
                if (storedTarget instanceof Double targetValue) {
                    currentTarget = targetValue;
                }
            }

            double target = clamp(currentTarget + delta, scrollPane.getVmin(), scrollPane.getVmax());

            Timeline timeline = new Timeline(
                    new KeyFrame(
                            animDuration,
                            new KeyValue(scrollPane.vvalueProperty(), target, Interpolator.EASE_BOTH)
                    )
            );
            scrollPane.getProperties().put(TIMELINE_KEY, timeline);
            scrollPane.getProperties().put("smoothScrollTarget", target);
            timeline.play();
            event.consume();
        });
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
