package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.h3common.Def;
import com.github.artyomcool.lodinfra.h3common.DefInfo;
import com.jfoenix.controls.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefPane extends StackPane {

    private static final DataFormat JAVA_FORMAT = new DataFormat("application/x-java-serialized-object");
    private static final String DROP_HINT_STYLE = "-fx-border-color: #eea82f; -fx-border-width: 0 0 2 0; -fx-padding: 3 3 1 3";

    private JFXTreeView<Object> createGroupsAndFrames() {
        JFXTreeView<Object> view = new JFXTreeView<>();
        view.setCellFactory(v -> {
            JFXTreeCell<Object> cell = new JFXTreeCell<>() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    super.setGraphic(null);

                    if (!empty) {
                        if (item instanceof DefInfo.Group group) {
                            String[] groups = DefInfo.groupNames(group.def);
                            setText(groups.length > group.groupIndex ? groups[group.groupIndex] : "Group " + group.groupIndex);
                        } else if (item instanceof Def.Frame frame) {
                            setText(frame.name);
                        }
                    }
                }
            };
            cell.setOnDragDetected((e) -> dragDetected(e, cell, v));
            cell.setOnDragOver((e) -> dragOver(e, cell));
            cell.setOnDragDropped((e) -> drop(e, cell, v));
            cell.setOnDragDone((e) -> clearDropLocation());
            return cell;
        });
        view.setPrefHeight(100000);
        view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        return view;
    }

    private final Label path = new Label();
    private final TextField fullWidth = new TextField();
    private final TextField fullHeight = new TextField();
    private final JFXComboBox<DefType> defType = new JFXComboBox<>();

    {
        defType.setItems(FXCollections.observableList(DefType.VALUES));
    }

    private final JFXTreeView<Object> groupsAndFrames = createGroupsAndFrames();
    private final DefView preview = new DefView();
    private final DefControl control = new DefControl(preview).noDiff();
    private final AnimationSpeedField animationSpeed = new AnimationSpeedField(this::nextFrame);
    private final JFXCheckBox lockGroup = new JFXCheckBox("Lock group");

    private final Button addGroup = new JFXButton("Add Group");
    private final Button insertFrames = new JFXButton("Insert Frames");
    private final VBox left = new VBox(2, groupsAndFrames, new HBox(2, addGroup, insertFrames));

    private final List<TreeItem<Object>> draggedItem = new ArrayList<>();
    private TreeCell<Object> dropZone;
    private final BorderPane right;

    {
        path.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        path.setMaxWidth(300);
        fullWidth.setPrefColumnCount(2);
        fullHeight.setPrefColumnCount(2);
        HBox top = new HBox(8, path, fullWidth, fullHeight, defType);
        HBox bottom = new HBox(8, control, animationSpeed, lockGroup);
        top.setAlignment(Pos.CENTER_LEFT);
        bottom.setAlignment(Pos.CENTER_LEFT);
        right = new BorderPane(preview, top, null, bottom, null);
        right.setPadding(new Insets(2, 2, 2, 2));

        addGroup.setOnAction(e -> {
            DefInfo.Group g = new DefInfo.Group(preview.getDef());
            g.groupIndex = g.def.groups.isEmpty() ? 0 : g.def.groups.get(g.def.groups.size() - 1).groupIndex + 1;
            TreeItem<Object> item = new TreeItem<>(g);
            groupsAndFrames.getRoot().getChildren().add(item);
        });
    }

    private final List<TreeItem<Object>> frames = new ArrayList<>();
    private boolean react = true;

    public DefPane() {
        SplitPane box = new SplitPane(left, right);
        box.setDividerPosition(0, 0.25);
        getChildren().setAll(box);

        HBox.setHgrow(groupsAndFrames, Priority.ALWAYS);
        HBox.setHgrow(control, Priority.ALWAYS);

        groupsAndFrames.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.getValue() instanceof DefInfo.Frame) {
                if (react) {
                    react = false;
                    preview.setFrame((DefInfo.Frame) newValue.getValue());
                    react = true;
                }
            }
        });
        control.playPause.setSelected(false);
        control.slider.setIndicatorPosition(JFXSlider.IndicatorPosition.LEFT);
        preview.addOnChangedListener(() -> {
            if (!react) {
                return;
            }
            int frameIndex = preview.getGlobalIndex();
            if (frameIndex >= 0 && frameIndex < frames.size()) {
                groupsAndFrames.getSelectionModel().clearSelection();
                groupsAndFrames.getSelectionModel().select(frames.get(frameIndex));
                groupsAndFrames.scrollTo(groupsAndFrames.getSelectionModel().getSelectedIndex());
            }
        });

        MenuItem delete = new MenuItem("Delete selected frames");
        delete.setOnAction(e -> {
            boolean removed = false;
            for (TreeItem<Object> item : groupsAndFrames.getSelectionModel().getSelectedItems()) {
                if (item.getValue() instanceof DefInfo.Frame) {
                    item.getParent().getChildren().remove(item);
                    removed = true;
                }
            }
            if (removed) {
                update(false);
            }
        });
        groupsAndFrames.setContextMenu(new ContextMenu(delete));

        insertFrames.setOnAction(e -> {
            TreeItem<Object> item = groupsAndFrames.getSelectionModel().getSelectedItem();
            if (item == null) {
                return;
            }
            DefInfo.Group parent;
            TreeItem<Object> parentItem;
            int index = 0;
            if (item.getValue() instanceof DefInfo.Group) {
                parent = (DefInfo.Group) item.getValue();
                parentItem = item;
            } else {
                parent = ((DefInfo.Frame) item.getValue()).group;
                parentItem = item.getParent();
                index = parentItem.getChildren().indexOf(item) + 1;
            }
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Resource File");
            List<File> files = fileChooser.showOpenMultipleDialog(getScene().getWindow());
            List<TreeItem<Object>> items = new ArrayList<>();
            for (File file : files) {
                DefInfo def = DefInfo.load(file.toPath());
                for (DefInfo.Group group : def.groups) {
                    for (DefInfo.Frame frame : group.frames) {
                        items.add(new TreeItem<>(frame.cloneBase(parent)));
                    }
                }
            }
            parentItem.getChildren().addAll(index, items);
            update(true);
        });
    }

    public void setDef(DefInfo def, DefInfo.Frame frame) {
        preview.setDef(def);
        path.setText(def.path == null ? "" : def.path.toString());
        fullWidth.setText(def.fullWidth + "");
        fullHeight.setText(def.fullHeight + "");
        defType.setValue(DefType.of(def.type));
/*
        def.palette;
        def.groups.get(0).groupIndex;
        def.groups.get(0).frames.get(0).compression;
        def.groups.get(0).frames.get(0).frameDrawType;
*/
        TreeItem<Object> root = new TreeItem<>();
        groupsAndFrames.setRoot(root);
        groupsAndFrames.setShowRoot(false);
        int nextIndex = 0;
        for (DefInfo.Group group : def.groups) {
            while (group.groupIndex > nextIndex) {
                DefInfo.Group g = new DefInfo.Group(def);
                g.groupIndex = nextIndex++;
                TreeItem<Object> item = new TreeItem<>(g);
                root.getChildren().add(item);
                item.setExpanded(true);
            }
            nextIndex++;
            TreeItem<Object> item = new TreeItem<>(group);
            root.getChildren().add(item);
            item.setExpanded(true);
            for (DefInfo.Frame f : group.frames) {
                TreeItem<Object> it = new TreeItem<>(f);
                item.getChildren().add(it);
                frames.add(it);
                if (f == frame) {
                    groupsAndFrames.getSelectionModel().select(it);
                }
            }
        }

        Platform.runLater(() -> Platform.runLater(() -> groupsAndFrames.scrollTo(groupsAndFrames.getSelectionModel().getSelectedIndex())));
    }

    public void start() {
        animationSpeed.start();
    }

    public void stop() {
        animationSpeed.stop();
    }

    private void nextFrame() {
        control.tick(lockGroup.isSelected());
    }

    private void dragDetected(MouseEvent event, TreeCell<Object> treeCell, TreeView<Object> view) {
        draggedItem.clear();
        // root can't be dragged
        if (!treeCell.getTreeItem().isLeaf()) {
            return;
        }

        List<TreeItem<Object>> items = view.getSelectionModel().getSelectedItems();
        for (TreeItem<Object> item : items) {
            if (item.isLeaf()) {
                draggedItem.add(item);
            }
        }

        Dragboard db = treeCell.startDragAndDrop(TransferMode.MOVE);

        db.setContent(Map.of(JAVA_FORMAT, new ArrayList<>(view.getSelectionModel().getSelectedIndices())));
        WritableImage image = treeCell.snapshot(null, null);
        int size = items.size();
        if (size > 1) {
            Group group = new Group();
            group.getChildren().add(new ImageView(image));
            Label text = new Label("+" + (size - 1));
            text.setPrefWidth(image.getWidth());
            text.setTextAlignment(TextAlignment.RIGHT);
            text.setAlignment(Pos.BOTTOM_RIGHT);
            text.setTextFill(Color.PALEGOLDENROD);
            text.setFont(Font.font("monospace", FontWeight.BOLD, 24));
            group.getChildren().add(text);
            Scene scene = new Scene(group);
            image = scene.snapshot(null);
        }
        db.setDragView(image);
        event.consume();
    }

    private void dragOver(DragEvent event, TreeCell<Object> treeCell) {
        if (!event.getDragboard().hasContent(JAVA_FORMAT)) {
            return;
        }

        event.acceptTransferModes(TransferMode.MOVE);
        if (!Objects.equals(dropZone, treeCell)) {
            clearDropLocation();
            this.dropZone = treeCell;
            dropZone.setStyle(DROP_HINT_STYLE);
        }
    }

    private void drop(DragEvent event, TreeCell<Object> treeCell, TreeView<Object> treeView) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (!db.hasContent(JAVA_FORMAT)) {
            return;
        }

        TreeItem<Object> thisItem = treeCell.getTreeItem();

        boolean hasItemInSelection = false;
        for (TreeItem<Object> dragItem : draggedItem) {
            if (dragItem == thisItem) {
                hasItemInSelection = true;
            } else {
                dragItem.getParent().getChildren().remove(dragItem);
            }
        }

        if (thisItem.getValue() instanceof DefInfo.Group) {
            thisItem.getChildren().addAll(0, draggedItem);
        } else {
            ObservableList<TreeItem<Object>> children = thisItem.getParent().getChildren();
            int indexInParent = children.indexOf(thisItem);
            if (hasItemInSelection) {
                children.remove(thisItem);
            } else {
                indexInParent++;
            }
            children.addAll(indexInParent, draggedItem);
        }
        react = false;
        treeView.getSelectionModel().clearSelection();
        for (TreeItem<Object> objectTreeItem : draggedItem) {
            treeView.getSelectionModel().select(objectTreeItem);
        }
        react = true;

        update(true);

        event.setDropCompleted(success);
    }

    private void update(boolean restoreSelection) {
        DefInfo.Frame currentFrame = restoreSelection
                ? (DefInfo.Frame) groupsAndFrames.getSelectionModel().getSelectedItem().getValue()
                : null;
        DefInfo def = preview.getDef().cloneBase();
        frames.clear();
        for (TreeItem<Object> treeGroup : groupsAndFrames.getRoot().getChildren()) {
            DefInfo.Group group = ((DefInfo.Group) treeGroup.getValue()).cloneBase(def);
            treeGroup.setValue(group);
            for (TreeItem<Object> treeFrame : treeGroup.getChildren()) {
                DefInfo.Frame prevFrame = (DefInfo.Frame) treeFrame.getValue();
                DefInfo.Frame frame = prevFrame.cloneBase(group);
                if (restoreSelection && currentFrame == prevFrame) {
                    currentFrame = frame;
                }
                treeFrame.setValue(frame);
                group.frames.add(frame);
                frames.add(treeFrame);
            }
            def.groups.add(group);
        }
        react = false;
        preview.setDef(def);
        if (restoreSelection) {
            preview.setFrame(currentFrame);
        }
        react = true;
        if (!restoreSelection) {
            preview.setCurrentIndex(0);
        }
    }

    private void clearDropLocation() {
        if (dropZone != null) {
            dropZone.setStyle("");
        }
    }

}
