package com.github.artyomcool.lodinfra.ui;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public class Ui {
    static RuntimeException showError(String error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(error);
        alert.showAndWait();
        return new RuntimeException(error);
    }

    static Pane groupButtons(Control... nodes) {
        HBox result = new HBox(2, nodes) {
            @Override
            protected void layoutChildren() {
                for (Control node : nodes) {
                    node.setPrefWidth((getWidth() - getSpacing() * (nodes.length - 1)) / nodes.length);
                }
                super.layoutChildren();
            }
        };
        result.setPadding(new Insets(2, 0, 2, 0));
        return result;
    }

    private static <T extends ButtonBase> T withAction(T button, Runnable action) {
        button.setOnAction(a -> action.run());
        return button;
    }

    static Button button(String name, Runnable action) {
        return withAction(new Button(name), action);
    }

    static JFXButton jfxbutton(String name, Runnable action) {
        return withAction(new JFXButton(name), action);
    }

    static <T extends Region> T pad(int padding, T node) {
        node.setPadding(new Insets(padding));
        return node;
    }

    static <T extends Region> T border(Color color, T node) {
        node.setBorder(new Border(new BorderStroke(color, BorderStrokeStyle.SOLID, null, BorderStroke.THIN)));
        return node;
    }

    private static <T extends Region> T bg(Color color, T node) {
        node.setBackground(new Background(new BackgroundFill(color, null, null)));
        return node;
    }

    static Node border(Color color, Node node) {
        return new VBox(new HBox(border(color, new StackPane(node))));
    }

    static <T extends Node> T grow(T node) {
        VBox.setVgrow(node, Priority.ALWAYS);
        HBox.setHgrow(node, Priority.ALWAYS);
        return node;
    }

    static <T extends Node> T growH(T node) {
        HBox.setHgrow(node, Priority.ALWAYS);
        return node;
    }

    static HBox line(Node... nodes) {
        return line(2, nodes);
    }

    static HBox line(int padding, Node... nodes) {
        return line(padding, 8, nodes);
    }

    static HBox line(int padding, int spacing, Node... nodes) {
        HBox box = pad(padding, new HBox(spacing, nodes));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    static <T extends Region> T width(int w, T node) {
        node.setPrefWidth(w);
        return node;
    }

    static <T extends Region> T height(int h, T node) {
        node.setPrefHeight(h);
        return node;
    }

    static Pane withLabel(Node pane, String label) {
        Pane result = new VBox(new Label(label), pane);
        result.setPadding(new Insets(4));
        return result;
    }
}
