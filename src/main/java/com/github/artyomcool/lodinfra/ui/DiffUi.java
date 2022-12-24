package com.github.artyomcool.lodinfra.ui;

import com.jfoenix.controls.JFXTreeTableView;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DiffUi extends Application {


    private final Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
    private final Insets padding = new Insets(2, 2, 2, 2);

    private final Path localPath;
    private final Path remotePath;
    private final Properties cfg;

    private Stage primaryStage;
    private PreviewNode previewLocal;
    private PreviewNode previewRemote;

    public DiffUi(Path localPath, Path remotePath, Path cfg) {
        this.localPath = localPath.toAbsolutePath();
        this.remotePath = remotePath.toAbsolutePath();

        try (BufferedReader stream = Files.newBufferedReader(cfg)) {
            this.cfg = new Properties();
            this.cfg.load(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record FileInfo(Path path, String name, FileTime lastModified, Long size, boolean isDirectory) {

        private static final SimpleDateFormat date = new SimpleDateFormat("yyyy.MM.dd");
        private static final SimpleDateFormat today = new SimpleDateFormat("HH:mm");

        public FileInfo foldInto(FileInfo local) {
            return new FileInfo(
                    path,
                    local.path.getParent().relativize(path).toString(),
                    lastModified,
                    size,
                    isDirectory
            );
        }

        public String lastModifiedText() {
            if (isDirectory) {
                return null;
            }
            if (lastModified == null) {
                return "<NO>";
            }
            if (lastModified.toInstant().isBefore(Instant.now().truncatedTo(ChronoUnit.DAYS))) {
                return date.format(lastModified.toMillis());
            }
            return today.format(lastModified.toMillis());
        }
    }

    private static class Item extends RecursiveTreeObject<Item> {
        final FileInfo local;
        final FileInfo remote;
        final ItemStatus status;

        Item(FileInfo local, FileInfo remote) {
            this.local = local;
            this.remote = remote;

            status = status();
        }

        public Item foldInto(Item value) {
            return new Item(local.foldInto(value.local), remote.foldInto(value.remote));
        }

        private ItemStatus status() {
            if (local.isDirectory != remote.isDirectory) {
                if (local.lastModified == null) {
                    return ItemStatus.REMOTE_NEWER;
                } else if (remote.lastModified == null) {
                    return ItemStatus.LOCAL_NEWER;
                }
                return ItemStatus.CONFLICT;
            }
            if (local.isDirectory) {
                return ItemStatus.SAME;
            }
            int compare = compare(local.lastModified, remote.lastModified);
            if (compare > 0) {
                return ItemStatus.LOCAL_NEWER;
            } else if (compare < 0) {
                return ItemStatus.REMOTE_NEWER;
            }
            return ItemStatus.SAME;
        }

        private int compare(FileTime o1, FileTime o2) {
            long t1 = o1 == null ? 0 : o1.toMillis() / 1000;
            long t2 = o2 == null ? 0 : o2.toMillis() / 1000;
            return Long.compare(t1, t2);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        try {
            primaryStage.setTitle("Directory diff");
            StackPane root = new StackPane();

            VBox lastFiles = files(root);
            previewLocal = preview();
            previewLocal.setAlignment(Pos.CENTER);
            previewRemote = preview();
            previewRemote.setAlignment(Pos.CENTER);
            VBox previews = new VBox(withLabel(previewLocal, "Preview A"), withLabel(previewRemote, "Perview B"));
            ScrollPane pane = new ScrollPane(previews);
            pane.setMinWidth(350);
            pane.setPrefWidth(350);
            pane.setMaxWidth(350);
            HBox box = new HBox(lastFiles, pane);
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

    private VBox files(StackPane root) throws IOException {
        JFXTreeTableView<Item> list = new JFXTreeTableView<>();

        TreeTableColumn<Item, String> name = new TreeTableColumn<>("Name");
        TreeTableColumn<Item, String> timeA = new TreeTableColumn<>("Local time");
        TreeTableColumn<Item, String> timeB = new TreeTableColumn<>("Remote time");
        TreeTableColumn<Item, Long> sizeA = new TreeTableColumn<>("Local size");
        TreeTableColumn<Item, Long> sizeB = new TreeTableColumn<>("Remote size");

        list.getColumns().addAll(
                name,
                timeA,
                timeB,
                sizeA,
                sizeB
        );
        VBox listParent = new VBox(list);
        VBox.setVgrow(list, Priority.ALWAYS);

        Font regular = Font.font(12);
        Font bold = Font.font(null, FontWeight.BOLD, 12);

        Map<Item, ItemAction> actions = new HashMap<>();

        name.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getValue().local.name + "          "));
        name.setCellFactory(new Callback<>() {
            @Override
            public TreeTableCell<Item, String> call(TreeTableColumn<Item, String> param) {
                return new TreeTableCell<>() {
                    private final Node SAME = createIcon(ItemStatus.SAME, ItemAction.NOTHING);

                    private final Node REMOTE_NEWER = createIcon(ItemStatus.REMOTE_NEWER, ItemAction.NOTHING);
                    private final Node REMOTE_NEWER_APPLY = createIcon(ItemStatus.REMOTE_NEWER, ItemAction.REMOTE_TO_LOCAL);
                    private final Node REMOTE_NEWER_REVERT = createIcon(ItemStatus.REMOTE_NEWER, ItemAction.LOCAL_TO_REMOTE);

                    private final Node LOCAL_NEWER = createIcon(ItemStatus.LOCAL_NEWER, ItemAction.NOTHING);
                    private final Node LOCAL_NEWER_APPLY = createIcon(ItemStatus.LOCAL_NEWER, ItemAction.LOCAL_TO_REMOTE);
                    private final Node LOCAL_NEWER_REVERT = createIcon(ItemStatus.LOCAL_NEWER, ItemAction.REMOTE_TO_LOCAL);

                    private final Node CONFLICT = createIcon(ItemStatus.CONFLICT, ItemAction.NOTHING);
                    private final Node CONFLICT_USE_REMOTE = createIcon(ItemStatus.CONFLICT, ItemAction.REMOTE_TO_LOCAL);
                    private final Node CONFLICT_USE_LOCAL = createIcon(ItemStatus.CONFLICT, ItemAction.LOCAL_TO_REMOTE);

                    private Node action(ItemAction action, Node nothing, Node remote, Node local) {
                        return switch (action) {
                            case NOTHING -> nothing;
                            case REMOTE_TO_LOCAL -> remote;
                            case LOCAL_TO_REMOTE -> local;
                        };
                    }
                    {
                        setTextOverrun(OverrunStyle.CLIP);
                    }

                    @Override
                    protected void updateItem(String text, boolean empty) {
                        super.updateItem(text, empty);

                        if (!empty) {
                            TreeItem<Item> treeItem = getTableRow().getTreeItem();
                            if (treeItem != null) {
                                Item item = treeItem.getValue();
                                ItemAction action = actions.getOrDefault(item, ItemAction.NOTHING);
                                super.setGraphic(
                                        switch (item.status) {
                                            case SAME -> SAME;
                                            case LOCAL_NEWER -> action(action, LOCAL_NEWER, LOCAL_NEWER_REVERT, LOCAL_NEWER_APPLY);
                                            case REMOTE_NEWER -> action(action, REMOTE_NEWER, REMOTE_NEWER_APPLY, REMOTE_NEWER_REVERT);
                                            case CONFLICT -> action(action, CONFLICT, CONFLICT_USE_REMOTE, CONFLICT_USE_LOCAL);
                                        }
                                );
                                if (action == item.status.negative()) {
                                    setTextFill(Color.RED);
                                    setFont(bold);
                                } else if (action == item.status.positive()) {
                                    setTextFill(Color.DARKBLUE);
                                    setFont(bold);
                                } else {
                                    setTextFill(Color.BLACK);
                                    setFont(regular);
                                }
                            } else {
                                super.setGraphic(REMOTE_NEWER);
                            }

                            super.setText(text);
                        } else {
                            super.setGraphic(null);
                            super.setText(null);
                        }

                    }
                };
            }
        });

        timeA.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getValue().local.lastModifiedText()));
        timeA.setCellFactory(new Callback<>() {

            @Override
            public TreeTableCell<Item, String> call(TreeTableColumn<Item, String> param) {
                return new TreeTableCell<>() {

                    @Override
                    protected void updateItem(String text, boolean empty) {
                        super.updateItem(text, empty);
                        super.setText(text);
                        super.setGraphic(null);

                        if (!empty) {
                            TreeItem<Item> treeItem = getTableRow().getTreeItem();
                            if (treeItem != null) {
                                Item item = treeItem.getValue();
                                if (item.status == ItemStatus.LOCAL_NEWER || item.status == ItemStatus.CONFLICT) {
                                    setFont(bold);
                                    return;
                                }
                            }
                        }
                        setFont(regular);
                    }
                };
            }
        });
        timeB.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getValue().remote.lastModifiedText()));
        timeB.setCellFactory(new Callback<>() {
            final Font regular = Font.font(12);
            final Font bold = Font.font(null, FontWeight.BOLD, 12);

            @Override
            public TreeTableCell<Item, String> call(TreeTableColumn<Item, String> param) {
                return new TreeTableCell<>() {

                    @Override
                    protected void updateItem(String text, boolean empty) {
                        super.updateItem(text, empty);
                        super.setGraphic(null);

                        if (empty) {
                            super.setText(null);
                            return;
                        }

                        super.setText(text);
                        TreeItem<Item> treeItem = getTableRow().getTreeItem();
                        if (treeItem != null) {
                            Item item = treeItem.getValue();
                            if (item.status == ItemStatus.REMOTE_NEWER || item.status == ItemStatus.CONFLICT) {
                                setFont(bold);
                                return;
                            }
                        }
                        setFont(regular);
                    }
                };
            }
        });

        sizeA.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getValue().local.size));
        sizeB.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getValue().remote.size));

        list.setRowFactory(r -> new TreeTableRow<>() {

            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    if (!item.local.isDirectory && !item.remote.isDirectory) {
                        setOnMouseClicked(e -> {
                            if (e.getClickCount() == 2) {
                                ItemAction a = actions.getOrDefault(item, ItemAction.NOTHING);
                                ItemAction target = e.isControlDown() ? item.status.negative() : item.status.positive();
                                if (a == target) {
                                    actions.put(item, ItemAction.NOTHING);
                                } else {
                                    actions.put(item, target);
                                }
                                r.refresh();
                                e.consume();
                            }
                        });
                    } else if (item.local.isDirectory && item.remote.isDirectory) {
                    }
                }
            }
        });
        list.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                show(list.getTreeItem(newValue.intValue()));
            }
        });

        String ignoreCommon = cfg.getProperty("ignore.common", "$^");
        Pattern ignoreLocal = Pattern.compile("(" + cfg.getProperty("ignore.local", "$^") + ")|(" + ignoreCommon + ")");
        Pattern ignoreRemote = Pattern.compile("(" + cfg.getProperty("ignore.remote", "$^") + ")|(" + ignoreCommon + ")");

        Set<Path> allPaths = new TreeSet<>();
        try (Stream<Path> w1 = Files.walk(localPath); Stream<Path> w2 = Files.walk(remotePath)) {
            w1.forEach(l -> allPaths.add(localPath.relativize(l)));
            w2.forEach(l -> allPaths.add(remotePath.relativize(l)));
        }

        TreeItem<Item> rootItem = new TreeItem<>(new Item(
                new FileInfo(localPath, localPath.toString(), Files.getLastModifiedTime(localPath), null, true),
                new FileInfo(remotePath, remotePath.toString(), Files.getLastModifiedTime(remotePath), null, true)
        ));

        Map<Path, TreeItem<Item>> expandedTree = new HashMap<>();

        for (Path path : allPaths) {
            if (path.getFileName().toString().isEmpty()) {
                expandedTree.put(path, rootItem);
                continue;
            }

            Path local = localPath.resolve(path);
            Path remote = remotePath.resolve(path);

            if (ignoreLocal.matcher(local.toString()).matches()) {
                continue;
            }
            if (ignoreRemote.matcher(remote.toString()).matches()) {
                continue;
            }

            boolean localExists = Files.exists(local);
            boolean remoteExists = Files.exists(remote);

            boolean localIsDirectory = Files.isDirectory(local);
            boolean remoteIsDirectory = Files.isDirectory(remote);

            FileInfo localFile = new FileInfo(
                    local,
                    path.getFileName().toString(),
                    localExists && !localIsDirectory ? Files.getLastModifiedTime(local) : null,
                    localExists && !localIsDirectory ? Files.size(local) : null,
                    localIsDirectory
            );
            FileInfo remoteFile = new FileInfo(
                    remote,
                    path.getFileName().toString(),
                    remoteExists && !remoteIsDirectory ? Files.getLastModifiedTime(remote) : null,
                    remoteExists && !remoteIsDirectory ? Files.size(remote) : null,
                    remoteIsDirectory
            );

            if (localExists && remoteExists) {
                if (!Files.isDirectory(local) && !Files.isDirectory(remote)) {
                    if (localFile.size != null && remoteFile.size != null && localFile.size.equals(remoteFile.size)) {
                        FileTime localModified = localFile.lastModified;
                        FileTime remoteModified = remoteFile.lastModified;
                        if (localModified.toMillis() / 1000 == remoteModified.toMillis() / 1000) {
                            continue;
                        }
                    }
                }
            }

            TreeItem<Item> parent = path.getParent() == null ? rootItem : expandedTree.get(path.getParent());
            TreeItem<Item> item = new TreeItem<>(new Item(localFile, remoteFile));
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
        if (treeItem == null) {
            return;
        }
        previewLocal.show(treeItem.getValue().local.path);
        previewRemote.show(treeItem.getValue().remote.path);
    }

    private boolean cleanup(TreeItem<Item> root) {
        for (Iterator<TreeItem<Item>> iterator = root.getChildren().iterator(); iterator.hasNext(); ) {
            TreeItem<Item> child = iterator.next();
            if (child.getValue().local.isDirectory && child.getValue().remote.isDirectory) {
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

    enum ItemStatus {
        SAME,
        LOCAL_NEWER {
            ItemAction negative() {
                return ItemAction.REMOTE_TO_LOCAL;
            }

            ItemAction positive() {
                return ItemAction.LOCAL_TO_REMOTE;
            }
        },
        REMOTE_NEWER,
        CONFLICT;

        ItemAction negative() {
            return ItemAction.LOCAL_TO_REMOTE;
        }

        ItemAction positive() {
            return ItemAction.REMOTE_TO_LOCAL;
        }
    }

    enum ItemAction {
        NOTHING,
        LOCAL_TO_REMOTE,
        REMOTE_TO_LOCAL
    }

    private static Node createIcon(ItemStatus status, ItemAction action) {
        return switch (status) {
            case SAME -> new Region();
            case LOCAL_NEWER -> group(
                    action(action, ItemAction.LOCAL_TO_REMOTE, Color.DARKGOLDENROD),
                    leftRect(14, Color.DARKGOLDENROD),
                    rightRect(8, Color.DARKGRAY)
            );
            case REMOTE_NEWER -> group(
                    action(action, ItemAction.REMOTE_TO_LOCAL, Color.MEDIUMSLATEBLUE),
                    leftRect(8, Color.DARKGRAY),
                    rightRect(14, Color.MEDIUMSLATEBLUE)
            );
            case CONFLICT -> group(
                    action(action, ItemAction.NOTHING, null),
                    leftRect(8, Color.DARKRED),
                    rightRect(8, Color.DARKRED)
            );
        };
    }

    private static Group group(Node action, Node left, Node right) {
        if (action == null) {
            return new Group(left, right);
        }
        return new Group(left, right, action);
    }

    private static Node leftRect(int h, Color color) {
        return rect(0, h, color);
    }

    private static Node rightRect(int h, Color color) {
        return rect(9, h, color);
    }

    private static SVGPath rect(int x, int h, Color color) {
        SVGPath path = new SVGPath();
        path.setContent("m " + x + " 15 6 0 0 -" + h + " -6 0 0 " + h + " z");
        path.setStroke(color);
        path.setFill(Color.LIGHTGRAY);
        path.setStrokeWidth(2);
        path.setStrokeType(StrokeType.INSIDE);
        return path;
    }

    private static Node action(ItemAction action, ItemAction positive, Color positiveColor) {
        if (action == ItemAction.NOTHING) {
            return null;
        }

        SVGPath path = new SVGPath();
        path.setFill(null);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        path.setStrokeLineCap(StrokeLineCap.ROUND);

        if (action == positive) {
            path.setStrokeWidth(2);
            path.setStroke(positiveColor);
            path.setContent(
                    action == ItemAction.REMOTE_TO_LOCAL
                            ? "m 1 5 2 2 2 -2 m -2 2 0 -5 9 0"
                            : "m 14 5 -2 2 -2 -2 m 2 2 0 -5 -9 0"
            );
        } else {
            path.setStrokeWidth(0.5);
            path.setStroke(Color.RED);
            path.setContent(
                    action == ItemAction.REMOTE_TO_LOCAL
                            ? "m 3 6 0 -3 8 0"
                            : "m 3 6 0 -3 8 0"
            );
            path.setContent(
                    action == ItemAction.REMOTE_TO_LOCAL
                            ? "M 2 1 1 2 M 5 1 1 5 M 5 3 1 8 M 5 5 1 13 M 5 7 1 15 M 5 9 3 15 M 5 11 5 15"
                            : "M 11 1 10 2 M 14 1 10 5 M 14 3 10 8 M 14 5 10 13 M 14 7 10 15 M 14 9 12 15 M 14 11 14 15"
            );
        }

        return path;
    }

}