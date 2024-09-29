package com.github.artyomcool.lodinfra.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXSlider;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.function.Consumer;

public class Ui {
    public static RuntimeException showError(String error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(error);
        alert.showAndWait();
        return new RuntimeException(error);
    }

    public static MenuItem menuItem(String title, Action action) {
        MenuItem menuItem = new MenuItem(title);
        menuItem.setOnAction(actionEvent -> {
            try {
                action.run();
            } catch (Exception e) {
                e.printStackTrace();
                showError("Unexpected error: " + e.getMessage());
            }
        });
        return menuItem;
    }

    public static Pane groupButtons(Control... nodes) {
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

    public static <T extends ButtonBase> T withAction(T button, Runnable action) {
        button.setOnAction(a -> action.run());
        return button;
    }

    public static Button button(String name) {
        return new Button(name);
    }

    public static Button button(String name, Runnable action) {
        return withAction(button(name), action);
    }

    public static <T> JFXComboBox<T> combo(List<T> objects, Consumer<? super T> listener) {
        JFXComboBox<T> combo = new JFXComboBox<>();
        combo.setItems(FXCollections.observableList(objects));
        if (!objects.isEmpty()) {
            combo.setValue(objects.get(0));
        }
        combo.valueProperty().addListener((observable, oldValue, newValue) -> listener.accept(newValue));
        return combo;
    }

    public static JFXSlider slider(int start, int step, int end, Consumer<? super Integer> listener) {
        JFXSlider slider = new JFXSlider(start, end, (end - start) / 2.);
        slider.blockIncrementProperty().setValue(step);
        slider.valueProperty().addListener((observable, oldValue, newValue) -> listener.accept(newValue.intValue()));
        return slider;
    }

    public static JFXButton jfxbutton(String name, Runnable action) {
        return withAction(new JFXButton(name), action);
    }

    public static <T extends Region> T pad(T node) {
        return pad(2, node);
    }

    public static <T extends Region> T pad(int padding, T node) {
        node.setPadding(new Insets(padding));
        return node;
    }

    public static <T extends Region> T border(Color color, T node) {
        node.setBorder(new Border(new BorderStroke(color, BorderStrokeStyle.SOLID, null, BorderStroke.THIN)));
        return node;
    }

    public static <T extends Region> T bg(Color color, T node) {
        node.setBackground(new Background(new BackgroundFill(color, null, null)));
        return node;
    }

    public static Node border(Color color, Node node) {
        return new VBox(new HBox(border(color, new StackPane(node))));
    }

    public static <T extends Node> T hide(T node) {
        node.setVisible(false);
        node.setManaged(false);
        return node;
    }

    public static <T extends Node> T grow(T node) {
        VBox.setVgrow(node, Priority.ALWAYS);
        HBox.setHgrow(node, Priority.ALWAYS);
        if (node instanceof Region) {
            ((Region) node).setMaxWidth(Double.MAX_VALUE);
            ((Region) node).setMaxHeight(Double.MAX_VALUE);
        }
        return node;
    }

    public static <T extends Node> T growH(T node) {
        HBox.setHgrow(node, Priority.ALWAYS);
        if (node instanceof Region) {
            ((Region) node).setMaxWidth(Double.MAX_VALUE);
        }
        return node;
    }

    public static <T extends Node> T growV(T node) {
        VBox.setVgrow(node, Priority.ALWAYS);
        if (node instanceof Region) {
            ((Region) node).setMaxHeight(Double.MAX_VALUE);
        }
        return node;
    }

    public static HBox line(Node... nodes) {
        return line(2, nodes);
    }

    public static HBox line(int padding, Node... nodes) {
        return line(padding, 8, nodes);
    }

    public static HBox line(int padding, int spacing, Node... nodes) {
        HBox box = pad(padding, new HBox(spacing, nodes));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox column(Node... nodes) {
        return column(2, nodes);
    }

    public static VBox column(int padding, Node... nodes) {
        return column(padding, 8, nodes);
    }

    public static VBox column(int padding, int spacing, Node... nodes) {
        return pad(padding, new VBox(spacing, nodes));
    }

    public static <T extends Region> T width(int w, T node) {
        node.setPrefWidth(w);
        return node;
    }

    public static <T extends Region> T height(int h, T node) {
        node.setPrefHeight(h);
        return node;
    }

    public static Node withLabel(Node pane, String label) {
        if (label == null) {
            return pane;
        }
        Label l = new Label(label);
        Pane result = new VBox(l, pane);
        result.setPadding(new Insets(2));
        if (pane instanceof Region r) {
            r.minWidthProperty().bind(l.widthProperty());
        }
        return result;
    }

    public static TextField textField(String defaultValue) {
        return new TextField(defaultValue);
    }

    public static interface Action {
        void run() throws Exception;
    }

}
