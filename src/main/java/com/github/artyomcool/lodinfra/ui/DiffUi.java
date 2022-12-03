package com.github.artyomcool.lodinfra.ui;

import com.jfoenix.controls.JFXTreeTableView;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

public class DiffUi extends Application {


    private final Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
    private final Insets padding = new Insets(2, 2, 2, 2);

    private final Path leftPath;
    private final Path rightPath;

    private Stage primaryStage;
    private PreviewNode previewLeft;
    private PreviewNode previewRight;

    public DiffUi(Path leftPath, Path rightPath) {
        this.leftPath = leftPath;
        this.rightPath = rightPath;
    }

    record FileInfo(Path path, String name, FileTime lastModified, Long size, boolean isDirectory) {

        private static final SimpleDateFormat date = new SimpleDateFormat("HH:mm");
        private static final SimpleDateFormat today = new SimpleDateFormat("yyyy.MM.dd");

        public FileInfo foldInto(FileInfo left) {
            return new FileInfo(
                    path,
                    left.path.getParent().relativize(path).toString(),
                    lastModified,
                    size,
                    isDirectory
            );
        }

        public String lastModifiedText() {
            if (lastModified == null) {
                return null;
            }
            if (lastModified.toInstant().isBefore(Instant.now().truncatedTo(ChronoUnit.DAYS))) {
                return date.format(lastModified.toMillis());
            }
            return today.format(lastModified.toMillis());
        }
    }

    private static class Item extends RecursiveTreeObject<Item> {
        final FileInfo left;
        final FileInfo right;

        Item(FileInfo left, FileInfo right) {
            this.left = left;
            this.right = right;
        }

        public Item foldInto(Item value) {
            return new Item(left.foldInto(value.left), right.foldInto(value.right));
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        try {
            primaryStage.setTitle("Directory diff");
            StackPane root = new StackPane();

            VBox lastFiles = files(root);
            previewLeft = preview();
            previewLeft.setAlignment(Pos.CENTER);
            previewRight = preview();
            previewRight.setAlignment(Pos.CENTER);
            VBox previews = new VBox(withLabel(previewLeft, "Preview A"), withLabel(previewRight, "Perview B"));
            previews.setMinWidth(300);
            HBox box = new HBox(lastFiles, previews);
            HBox.setHgrow(lastFiles, Priority.ALWAYS);
            root.getChildren().add(box);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());

            primaryStage.setMinWidth(1024);
            primaryStage.setMinHeight(800);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int compare(FileTime o1, FileTime o2) {
        long t1 = o1 == null ? 0 : o1.toMillis() / 1000;
        long t2 = o2 == null ? 0 : o2.toMillis() / 1000;
        return Long.compare(t1, t2);
    }

    private VBox files(StackPane root) throws IOException {
        JFXTreeTableView<Item> list = new JFXTreeTableView<>();

        TreeTableColumn<Item, String> nameA = new TreeTableColumn<>("Name A");
        TreeTableColumn<Item, String> nameB = new TreeTableColumn<>("Name B");
        TreeTableColumn<Item, String> timeA = new TreeTableColumn<>("Time A");
        TreeTableColumn<Item, String> timeB = new TreeTableColumn<>("Time B");
        TreeTableColumn<Item, Long> sizeA = new TreeTableColumn<>("Size A");
        TreeTableColumn<Item, Long> sizeB = new TreeTableColumn<>("Size B");

        list.getColumns().addAll(
                nameA,
                nameB,
                timeA,
                timeB,
                sizeA,
                sizeB
        );
        VBox listParent = new VBox(list);
        VBox.setVgrow(list, Priority.ALWAYS);

        Set<Item> checked = new HashSet<>();
        Set<Item> reverted = new HashSet<>();

        nameA.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getValue().left.name + "     "));
        nameB.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getValue().right.name + "     "));

        timeA.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getValue().left.lastModifiedText()));
        timeB.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getValue().right.lastModifiedText()));

        sizeA.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getValue().left.size));
        sizeB.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getValue().right.size));

        list.setRowFactory(r -> new TreeTableRow<>() {

            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("left-newer", "right-newer", "checked");
                if (!empty) {
                    if (!item.left.isDirectory && !item.right.isDirectory) {
                        int compare = compare(item.left.lastModified, item.right.lastModified);
                        if (compare < 0) {
                            getStyleClass().add("left-newer");
                        } else if (compare > 0) {
                            getStyleClass().add("right-newer");
                        }
                        if (checked.contains(item)) {
                            getStyleClass().add("checked");
                        }
                        setOnMouseClicked(e -> {
                            if (e.getClickCount() == 2) {
                                if (e.isControlDown()) {
                                    if (reverted.remove(item)) {
                                        getStyleClass().remove("reverted");
                                    } else {
                                        reverted.add(item);
                                        checked.remove(item);
                                        getStyleClass().add("reverted");
                                    }
                                } else {
                                    if (checked.remove(item)) {
                                        getStyleClass().remove("checked");
                                    } else {
                                        checked.add(item);
                                        reverted.remove(item);
                                        getStyleClass().add("checked");
                                    }
                                }
                            }
                        });
                    } else if (item.left.isDirectory && item.right.isDirectory) {
                    }
                }
            }
        });
        list.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (newValue != null) {
                    show(list.getTreeItem(newValue.intValue()));
                }
            }
        });

        Set<Path> allPaths = new TreeSet<>();
        try (Stream<Path> w1 = Files.walk(leftPath); Stream<Path> w2 = Files.walk(rightPath)) {
            w1.forEach(l -> allPaths.add(leftPath.relativize(l)));
            w2.forEach(l -> allPaths.add(rightPath.relativize(l)));
        }

        TreeItem<Item> rootItem = new TreeItem<>(new Item(
                new FileInfo(leftPath, leftPath.toString(), Files.getLastModifiedTime(leftPath), null, true),
                new FileInfo(rightPath, rightPath.toString(), Files.getLastModifiedTime(rightPath), null, true)
        ));

        Map<Path, TreeItem<Item>> expandedTree = new HashMap<>();

        for (Path path : allPaths) {
            if (path.getFileName().toString().isEmpty()) {
                expandedTree.put(path, rootItem);
                continue;
            }

            Path left = leftPath.resolve(path);
            Path right = rightPath.resolve(path);

            boolean leftExists = Files.exists(left);
            boolean rightExists = Files.exists(right);

            boolean leftIsDirectory = Files.isDirectory(left);
            boolean rightIsDirectory = Files.isDirectory(right);

            FileInfo leftFile = new FileInfo(
                    left,
                    path.getFileName().toString(),
                    leftExists && !leftIsDirectory ? Files.getLastModifiedTime(left) : null,
                    leftExists && !leftIsDirectory ? Files.size(left) : null,
                    leftIsDirectory
            );
            FileInfo rightFile = new FileInfo(
                    right,
                    path.getFileName().toString(),
                    rightExists && !rightIsDirectory ? Files.getLastModifiedTime(right) : null,
                    rightExists && !rightIsDirectory ? Files.size(right) : null,
                    rightIsDirectory
            );

            if (leftExists && rightExists) {
                if (!Files.isDirectory(left) && !Files.isDirectory(right)) {
                    if (leftFile.size != null && rightFile.size != null && leftFile.size.equals(rightFile.size)) {
                        FileTime leftModified = leftFile.lastModified;
                        FileTime rightModified = rightFile.lastModified;
                        if (leftModified.toMillis() / 1000 == rightModified.toMillis() / 1000) {
                            continue;
                        }
                    }
                }
            }

            TreeItem<Item> parent = path.getParent() == null ? rootItem : expandedTree.get(path.getParent());
            TreeItem<Item> item = new TreeItem<>(new Item(leftFile, rightFile));
            parent.getChildren().add(item);
            expandedTree.put(path, item);
        }

        cleanup(rootItem);
        fold(rootItem);
        expand(rootItem);

        list.setRoot(rootItem);
        list.setShowRoot(false);

        return listParent;
    }

    private void show(TreeItem<Item> treeItem) {
        previewLeft.show(treeItem.getValue().left.path);
        previewRight.show(treeItem.getValue().right.path);
    }

    private boolean cleanup(TreeItem<Item> root) {
        for (Iterator<TreeItem<Item>> iterator = root.getChildren().iterator(); iterator.hasNext(); ) {
            TreeItem<Item> child = iterator.next();
            if (child.getValue().left.isDirectory && child.getValue().right.isDirectory) {
                if (cleanup(child)) {
                    iterator.remove();
                }
            }
        }
        return root.isLeaf();
    }

    private PreviewNode preview() {
        return new PreviewNode();
    }

    private TreeItem<Item> fold(TreeItem<Item> root) {
        if (root.getChildren().size() == 1) {
            TreeItem<Item> child = root.getChildren().get(0);
            fold(child);
            if (child.isLeaf()) {
                return root;
            }
            child.setValue(child.getValue().foldInto(root.getValue()));
            return child;
        }

        for (ListIterator<TreeItem<Item>> iterator = root.getChildren().listIterator(); iterator.hasNext(); ) {
            TreeItem<Item> child = iterator.next();
            iterator.set(fold(child));
        }
        return root;
    }

    private void expand(TreeItem<Item> rootItem) {
        rootItem.setExpanded(true);
        rootItem.getChildren().forEach(this::expand);
    }

    private Pane withLabel(Node pane, String label) {
        Pane result = new VBox(new Label(label), pane);
        result.setPadding(new Insets(4));
        return result;
    }
}