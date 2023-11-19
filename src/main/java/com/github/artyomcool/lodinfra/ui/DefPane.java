package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.h3common.Def;
import com.github.artyomcool.lodinfra.h3common.DefInfo;
import com.jfoenix.controls.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class DefPane extends StackPane {

    private static JFXTreeView<Object> createGroupsAndFrames() {
        JFXTreeView<Object> view = new JFXTreeView<>();
        view.setCellFactory(param -> new JFXTreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                super.setGraphic(null);

                if (!empty) {
                    if (item instanceof DefInfo.Group group) {
                        String[] groups = DefInfo.groupNames(group.def.groups.size());
                        setText(groups.length > group.groupIndex ? groups[group.groupIndex] : "Group " + group.groupIndex);
                    } else if (item instanceof Def.Frame frame) {
                        setText(frame.name);
                    }
                }
            }
        });
        view.setPrefHeight(100000);
        return view;
    }
    private final JFXTreeView<Object> groupsAndFrames = createGroupsAndFrames();

    private final VBox left = new VBox(groupsAndFrames, new Button("Add Group"));
    private final VBox right = new VBox();

    public DefPane() {
        HBox.setHgrow(groupsAndFrames, Priority.ALWAYS);
        left.setSpacing(2);
        SplitPane box = new SplitPane(left, right);
        box.setDividerPosition(0, 0.25);
        getChildren().setAll(box);
    }

    public void setDef(Def def) {
        TreeItem<Object> root = new TreeItem<>();
        groupsAndFrames.setRoot(root);
        groupsAndFrames.setShowRoot(false);
        for (Def.Group group : def.groups) {
            TreeItem<Object> item = new TreeItem<>(group);
            item.setExpanded(true);
            for (Def.Frame frame : group.frames) {
                TreeItem<Object> it = new TreeItem<>(frame);
                item.getChildren().add(it);
            }
            root.getChildren().add(item);
        }
    }

}
