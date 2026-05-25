package com.lanmessenger;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import java.util.Set;

public final class SmoothScrollUtil {
    private static final String TIMELINE_KEY = "smoothScrollTimeline";
    private static final String TARGET_KEY = "smoothScrollTarget";
    private static final String INSTALLED_KEY = "smoothScrollInstalled";
    private static final String LIST_TIMELINE_KEY = "smoothListScrollTimeline";
    private static final String LIST_TARGET_KEY = "smoothListScrollTarget";
    private static final String LIST_INSTALLED_KEY = "smoothListScrollInstalled";
    private static final double SCROLL_FACTOR = 0.0018;
    private static final Duration ANIM_DURATION = Duration.millis(150);
    private static final double FAST_SCROLL_FACTOR = 0.0075;
    private static final Duration FAST_ANIM_DURATION = Duration.millis(45);
    private static final double LIGHT_SCROLL_FACTOR = 0.00165;
    private static final Duration LIGHT_ANIM_DURATION = Duration.millis(110);
    private static final Duration LIST_ANIM_DURATION = Duration.millis(140);

    private SmoothScrollUtil() {}

    public static void apply(ScrollPane scrollPane) {
        install(scrollPane, SCROLL_FACTOR, ANIM_DURATION);
    }

    public static void applyFast(ScrollPane scrollPane) {
        install(scrollPane, FAST_SCROLL_FACTOR, FAST_ANIM_DURATION);
    }

    public static void applyLight(ScrollPane scrollPane) {
        install(scrollPane, LIGHT_SCROLL_FACTOR, LIGHT_ANIM_DURATION);
    }

    public static void apply(ListView<?> listView) {
        install(listView);
    }

    public static void applyToScene(Scene scene) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        applyToNodeTree(scene.getRoot());
    }

    public static void applyToNodeTree(Parent root) {
        if (root == null) {
            return;
        }
        visitNode(root);
    }

    private static void visitNode(Node node) {
        if (node == null) {
            return;
        }
        if (node instanceof ScrollPane scrollPane) {
            apply(scrollPane);
        } else if (node instanceof ListView<?> listView) {
            apply(listView);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                visitNode(child);
            }
        }
    }

    private static void install(ScrollPane scrollPane, double scrollFactor, Duration animDuration) {
        if (scrollPane == null) return;
        if (Boolean.TRUE.equals(scrollPane.getProperties().get(INSTALLED_KEY))) return;

        scrollPane.getProperties().put(INSTALLED_KEY, true);
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0) return;

            double delta = -event.getDeltaY() * scrollFactor;
            Object existing = scrollPane.getProperties().get(TIMELINE_KEY);
            double currentTarget = scrollPane.getVvalue();
            if (existing instanceof Timeline timeline) {
                timeline.stop();
                Object storedTarget = scrollPane.getProperties().get(TARGET_KEY);
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
            scrollPane.getProperties().put(TARGET_KEY, target);
            timeline.play();
            event.consume();
        });
    }

    private static void install(ListView<?> listView) {
        if (listView == null) {
            return;
        }
        if (Boolean.TRUE.equals(listView.getProperties().get(LIST_INSTALLED_KEY))) {
            return;
        }

        listView.getProperties().put(LIST_INSTALLED_KEY, true);
        listView.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0) {
                return;
            }

            ScrollBar vBar = resolveVerticalBar(listView);
            if (vBar == null) {
                return;
            }

            double pageSize = Math.max(0.08, vBar.getVisibleAmount() * 0.8);
            double delta = (-event.getDeltaY() / 40.0) * pageSize;

            Object existing = listView.getProperties().get(LIST_TIMELINE_KEY);
            double currentTarget = vBar.getValue();
            if (existing instanceof Timeline timeline) {
                timeline.stop();
                Object stored = listView.getProperties().get(LIST_TARGET_KEY);
                if (stored instanceof Double targetValue) {
                    currentTarget = targetValue;
                }
            }

            double target = clamp(currentTarget + delta, vBar.getMin(), vBar.getMax());
            Timeline timeline = new Timeline(
                    new KeyFrame(
                            LIST_ANIM_DURATION,
                            new KeyValue(vBar.valueProperty(), target, Interpolator.EASE_BOTH)
                    )
            );
            timeline.setOnFinished(e -> {
                if (timeline.getStatus() == Animation.Status.STOPPED) {
                    listView.getProperties().remove(LIST_TIMELINE_KEY);
                }
            });

            listView.getProperties().put(LIST_TIMELINE_KEY, timeline);
            listView.getProperties().put(LIST_TARGET_KEY, target);
            timeline.play();
            event.consume();
        });

        listView.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(() -> resolveVerticalBar(listView));
            }
        });
        Platform.runLater(() -> resolveVerticalBar(listView));
    }

    private static ScrollBar resolveVerticalBar(ListView<?> listView) {
        Set<Node> nodes = listView.lookupAll(".scroll-bar");
        for (Node node : nodes) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                return bar;
            }
        }
        return null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
