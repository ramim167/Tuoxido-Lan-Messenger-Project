package com.lanmessenger;

import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

public final class MomentMenuSupport {
    private MomentMenuSupport() {}

    public static Button createMenuButton(Runnable onEditCaption, Runnable onChangePicture, Runnable onDelete) {
        Button menuBtn = new Button("⋮");
        menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -lm-muted; -fx-cursor: hand; -fx-font-weight: bold; -fx-font-size: 16px;");

        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("custom-menu");

        if (onEditCaption != null) {
            MenuItem editItem = new MenuItem("Edit caption");
            editItem.setOnAction(e -> onEditCaption.run());
            menu.getItems().add(editItem);
        }

        if (onChangePicture != null) {
            MenuItem changePicItem = new MenuItem("Change Pic");
            changePicItem.setOnAction(e -> onChangePicture.run());
            menu.getItems().add(changePicItem);
        }

        if (onDelete != null) {
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(e -> onDelete.run());
            menu.getItems().add(deleteItem);
        }

        menuBtn.setOnMouseClicked(e -> {
            menu.show(menuBtn, e.getScreenX(), e.getScreenY());
            if (menu.getScene() != null && menuBtn.getScene() != null) {
                menu.getScene().getStylesheets().setAll(menuBtn.getScene().getStylesheets());
                if (!menu.getScene().getRoot().getStyleClass().contains("root")) {
                    menu.getScene().getRoot().getStyleClass().add("root");
                }
            }
            e.consume();
        });

        return menuBtn;
    }
}
