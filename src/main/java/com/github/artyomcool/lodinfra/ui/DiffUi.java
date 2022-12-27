package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.LodFile;
import com.jfoenix.controls.JFXTreeTableView;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.Event;
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
import org.controlsfx.control.SegmentedButton;

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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DiffUi extends Application {


    private final Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
    private final Insets padding = new Insets(2, 2, 2, 2);

    private final Path localPath;
    private final Path remotePath;
    private final Properties cfg;

    private Map<Item, ItemAction> actions;

    private Stage primaryStage;
    private PreviewNode previewLocal;
    private PreviewNode previewRemote;
    private TreeItem<Item> rootItem;

    public DiffUi(Path localPath, Path remotePath, Path cfg) {
        this.localPath = localPath.toAbsolutePath();
        this.remotePath = remotePath.toAbsolutePath();

        try (BufferedReader stream = Files.newBufferedReader(cfg)) {
            this.cfg = new Properties();
            this.cfg.load(stream);

            System.out.println();
            System.out.println("DiffUi cfg " + cfg);
            for (String propertyName : this.cfg.stringPropertyNames()) {
                System.out.println(propertyName + "=" + this.cfg.getProperty(propertyName));
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record FileInfo(Path path, String name, FileTime lastModified, Long size, boolean isDirectory, boolean isFile) {

        private static final SimpleDateFormat date = new SimpleDateFormat("dd.MM.yyyy");
        private static final SimpleDateFormat today = new SimpleDateFormat("HH:mm");

        public FileInfo foldInto(FileInfo local) {
            return new FileInfo(
                    path,
                    local.path.getParent().relativize(path).toString(),
                    lastModified,
                    size,
                    isDirectory,
                    isFile
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
        final boolean isSynthetic;

        Item(FileInfo local, FileInfo remote) {
            this(local, remote, false);
        }

        Item(FileInfo local, FileInfo remote, boolean isSynthetic) {
            this.local = local;
            this.remote = remote;
            this.isSynthetic = isSynthetic;

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

    private void executeCheckTreeItem(TreeItem<Item> treeItem, boolean checked) {
        Item item = treeItem.getValue();
        if (checked) {
            ItemAction positive = item.status().positive();
            markWithChildren(treeItem, positive);
            a: while (treeItem.getParent() != null) {
                treeItem = treeItem.getParent();
                item = treeItem.getValue();
                for (TreeItem<Item> child : treeItem.getChildren()) {
                    if (actions.get(child.getValue()) != positive) {
                        break a;
                    }
                }
                actions.put(item, positive);
                Event.fireEvent(treeItem, new TreeItem.TreeModificationEvent<>(TreeItem.valueChangedEvent(), treeItem, item));
            }
        } else {
            markWithChildren(treeItem, ItemAction.NOTHING);
            while (treeItem.getParent() != null) {
                treeItem = treeItem.getParent();
                item = treeItem.getValue();
                if (actions.getOrDefault(item, ItemAction.NOTHING) == ItemAction.NOTHING) {
                    break;
                }
                actions.put(item, ItemAction.NOTHING);
                Event.fireEvent(treeItem, new TreeItem.TreeModificationEvent<>(TreeItem.valueChangedEvent(), treeItem, item));
            }
        }
    }

    private void markWithChildren(TreeItem<Item> treeItem, ItemAction action) {
        Item item = treeItem.getValue();
        if (actions.put(item, action) != action) {
            Event.fireEvent(treeItem, new TreeItem.TreeModificationEvent<>(TreeItem.valueChangedEvent(), treeItem, item));
        }
        for (TreeItem<Item> child : treeItem.getChildren()) {
            markWithChildren(child, action);
        }
    }

    private VBox files(StackPane root) throws IOException {
        ToggleButton fetch = new ToggleButton("Fetch");
        fetch.setGraphic(downloadIcon());
        ToggleButton push = new ToggleButton("Push");
        ToggleButton observe = new ToggleButton("Observe");
        SegmentedButton modes = new SegmentedButton(
                fetch,
                push,
                observe
        );
        modes.getToggleGroup().selectedToggleProperty().addListener((obsVal, oldVal, newVal) -> {
            if (newVal == null) {
                oldVal.setSelected(true);
            }
        });

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
        VBox listParent = new VBox(modes, list);
        listParent.setAlignment(Pos.CENTER);
        VBox.setMargin(modes, padding);
        VBox.setVgrow(list, Priority.ALWAYS);

        Font regular = Font.font(12);
        Font bold = Font.font(null, FontWeight.BOLD, 12);

        name.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getValue().local.name + " "));
        name.setCellFactory(new Callback<>() {
            @Override
            public TreeTableCell<Item, String> call(TreeTableColumn<Item, String> param) {
                return new TreeTableCell<>() {

                    boolean ignoreUpdate = false;
                    private final CheckBox checkBox = new CheckBox();
                    {
                        checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                            if (ignoreUpdate) {
                                return;
                            }
                            TreeItem<Item> treeItem = getTableRow().getTreeItem();
                            executeCheckTreeItem(treeItem, newValue);
                        });
                        checkBox.setFocusTraversable(false);
                    }

                    @Override
                    protected double computePrefWidth(double height) {
                        double r = super.computePrefWidth(height);
                        if (height == -1) {
                            r += getTableRow().getTreeTableView().getTreeItemLevel(getTableRow().getTreeItem()) * 18;
                        }
                        return r;
                    }

                    @Override
                    protected void updateItem(String text, boolean empty) {
                        super.updateItem(text, empty);

                        if (!empty) {
                            TreeItem<Item> treeItem = getTableRow().getTreeItem();
                            if (treeItem != null) {
                                Item item = treeItem.getValue();
                                ItemAction action = actions.getOrDefault(item, ItemAction.NOTHING);
                                if (observe.isSelected()) {
                                    if (item.status == ItemStatus.CONFLICT) {
                                        setTextFill(Color.RED);
                                        setFont(bold);
                                    } else if (item.status == ItemStatus.REMOTE_NEWER) {
                                        setTextFill(Color.DARKBLUE);
                                        setFont(bold);
                                    } else if (item.status == ItemStatus.LOCAL_NEWER) {
                                        setTextFill(Color.DARKGOLDENROD);
                                        setFont(bold);
                                    } else {
                                        setTextFill(Color.BLACK);
                                        setFont(regular);
                                    }

                                    super.setGraphic(null);
                                } else {

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

                                    if (!item.isSynthetic) {
                                        boolean old = ignoreUpdate;
                                        ignoreUpdate = true;
                                        checkBox.setSelected(action == item.status.positive());
                                        ignoreUpdate = old;
                                        super.setGraphic(checkBox);
                                    } else {
                                        super.setGraphic(null);
                                    }
                                }
                            } else {
                                super.setGraphic(null);
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

        timeA.setCellValueFactory(param -> new SimpleStringProperty(
                !param.getValue().getValue().remote.isFile && !param.getValue().getValue().local.isFile
                        ? null
                        : param.getValue().getValue().local.lastModifiedText()
        ));
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
        timeB.setCellValueFactory(param -> new SimpleStringProperty(
                !param.getValue().getValue().remote.isFile && !param.getValue().getValue().local.isFile
                        ? null
                        : param.getValue().getValue().remote.lastModifiedText())
        );
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
                                TreeItem<Item> treeItem = getTreeItem();
                                if (treeItem.getChildren().isEmpty() && (item.local.isFile || item.remote.isFile)) {
                                    if (item.local.name.toLowerCase().endsWith(".lod")) {
                                        TreeSet<String> allResources = new TreeSet<>();
                                        Map<String, LodFile.SubFileMeta> localMapping = new HashMap<>();
                                        Map<String, LodFile.SubFileMeta> remoteMapping = new HashMap<>();

                                        try {
                                            if (item.local.isFile) {
                                                LodFile lod = LodFile.load(item.local.path);
                                                for (LodFile.SubFileMeta subFile : lod.subFiles) {
                                                    String name = new String(subFile.name).trim().toLowerCase();
                                                    allResources.add(name);
                                                    localMapping.put(name, subFile);
                                                }
                                            }
                                            if (item.remote.isFile) {
                                                LodFile lod = LodFile.load(item.remote.path);
                                                for (LodFile.SubFileMeta subFile : lod.subFiles) {
                                                    String name = new String(subFile.name).trim().toLowerCase();
                                                    allResources.add(name);
                                                    remoteMapping.put(name, subFile);
                                                }
                                            }

                                            List<TreeItem<Item>> items = new ArrayList<>();
                                            for (String res : allResources) {
                                                LodFile.SubFileMeta localMeta = localMapping.get(res);
                                                LodFile.SubFileMeta remoteMeta = remoteMapping.get(res);

                                                String localName = localMeta == null
                                                        ? new String(remoteMeta.name).trim()
                                                        : new String(localMeta.name).trim();

                                                String remoteName = remoteMeta == null
                                                        ? new String(localMeta.name).trim()
                                                        : new String(remoteMeta.name).trim();


                                                FileInfo localFile = new FileInfo(
                                                        item.local.path.resolveSibling(item.local.path.getFileName() + "?" + localName),
                                                        localName,
                                                        null,
                                                        localMeta == null ? null : (long)localMeta.uncompressedSize,
                                                        false,
                                                        localMeta != null
                                                );
                                                FileInfo remoteFile = new FileInfo(
                                                        item.remote.path.resolveSibling(item.remote.path.getFileName() + "?" + remoteName),
                                                        remoteName,
                                                        null,
                                                        remoteMeta == null ? null : (long)remoteMeta.uncompressedSize,
                                                        false,
                                                        remoteMeta != null
                                                );

                                                items.add(new TreeItem<>(new Item(localFile, remoteFile, true)));
                                            }
                                            treeItem.getChildren().setAll(items);
                                            treeItem.setExpanded(true);
                                        } catch (IOException ex) {
                                            ex.printStackTrace();
                                        }
                                    }
                                }
                            }
                        });
                    } else if (item.local.isDirectory && item.remote.isDirectory) {
                    }
                }
            }
        });
        list.setOnKeyTyped(k -> {
            if (!" ".equals(k.getCharacter())) {
                return;
            }
            TreeItem<Item> treeItem = list.getSelectionModel().getSelectedItem();
            if (treeItem == null) {
                return;
            }
            Item item = treeItem.getValue();
            ItemAction a = actions.getOrDefault(item, ItemAction.NOTHING);
            ItemAction target = item.status.positive();
            executeCheckTreeItem(treeItem, a != target);
        });
        list.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                show(list.getTreeItem(newValue.intValue()));
            }
        });

        rootItem = loadTree();

        list.setShowRoot(false);
        list.setFixedCellSize(20);

        fetch.selectedProperty().addListener(new ChangeListener<>() {

            final Map<Item, ItemAction> fetchActions = new HashMap<>();

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    actions = fetchActions;
                    list.setRoot(DiffUi.this.filterForFetch());
                    list.refresh();
                }
            }
        });
        push.selectedProperty().addListener(new ChangeListener<>() {

            final Map<Item, ItemAction> pushActions = new HashMap<>();

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    //actions = pushActions;
                    //list.setRoot(filterForPush());
                }
            }
        });
        observe.selectedProperty().addListener(new ChangeListener<>() {

            final Map<Item, ItemAction> observeActions = new HashMap<>();

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    actions = observeActions;
                    list.setRoot(DiffUi.this.filterForObserve());
                    list.refresh();
                }
            }
        });

        fetch.fire();
        executeCheckTreeItem(list.getRoot(), true);

        return listParent;
    }

    private TreeItem<Item> filterForObserve() {
        Predicate<TreeItem<Item>> preFilter = item -> true;
        Function<TreeItem<Item>, TreeItem<Item>> fold = item -> {
            if (item.getChildren().size() == 1) {
                item.setValue(item.getChildren().remove(0).getValue().foldInto(item.getValue()));
            } else if (item.getChildren().isEmpty()) {
                if (!item.getValue().local.isFile && !item.getValue().remote.isFile) {
                    return null;
                }
            }
            return item;
        };
        return filter(rootItem, preFilter, fold);
    }

    private TreeItem<Item> filterForFetch() {
        Predicate<TreeItem<Item>> preFilter = item -> {
            if (!item.getChildren().isEmpty()) {
                return true;
            }
            return item.getValue().status == ItemStatus.REMOTE_NEWER
                    || item.getValue().status == ItemStatus.CONFLICT;
        };
        Function<TreeItem<Item>, TreeItem<Item>> fold = item -> {
            if (item.getChildren().size() == 1) {
                TreeItem<Item> treeItem = item.getChildren().remove(0);
                treeItem.setValue(treeItem.getValue().foldInto(item.getValue()));
                return treeItem;
            } else if (item.getChildren().isEmpty()) {
                if (!item.getValue().local.isFile && !item.getValue().remote.isFile) {
                    return null;
                }
            }
            item.setExpanded(true);
            return item;
        };
        return filter(rootItem, preFilter, fold);
    }

    private TreeItem<Item> filter(
            TreeItem<Item> item,
            Predicate<TreeItem<Item>> preFilter,
            Function<TreeItem<Item>, TreeItem<Item>> fold
    ) {
        if (!preFilter.test(item)) {
            return null;
        }
        ObservableList<TreeItem<Item>> children = item.getChildren();
        item = new TreeItem<>(item.getValue());

        for (TreeItem<Item> child : children) {
            TreeItem<Item> clone = filter(child, preFilter, fold);
            if (clone != null) {
                item.getChildren().add(clone);
            }
        }

        return fold.apply(item);
    }

    private TreeItem<Item> loadTree() throws IOException {
        Set<Path> allPaths = loadAllPaths();

        TreeItem<Item> rootItem = new TreeItem<>(new Item(
                new FileInfo(localPath, localPath.toString(), Files.getLastModifiedTime(localPath), null, true, false),
                new FileInfo(remotePath, remotePath.toString(), Files.getLastModifiedTime(remotePath), null, true, false)
        ));

        Map<Path, TreeItem<Item>> expandedTree = new HashMap<>();

        for (Path path : allPaths) {
            if (path.getFileName().toString().isEmpty()) {
                expandedTree.put(path, rootItem);
                continue;
            }

            Path local = localPath.resolve(path);
            Path remote = remotePath.resolve(path);

            boolean localExists = Files.exists(local);
            boolean remoteExists = Files.exists(remote);

            boolean localIsDirectory = Files.isDirectory(local);
            boolean remoteIsDirectory = Files.isDirectory(remote);

            FileInfo localFile = new FileInfo(
                    local,
                    path.getFileName().toString(),
                    localExists && !localIsDirectory ? Files.getLastModifiedTime(local) : null,
                    localExists && !localIsDirectory ? Files.size(local) : null,
                    localIsDirectory,
                    localExists && !localIsDirectory
            );
            FileInfo remoteFile = new FileInfo(
                    remote,
                    path.getFileName().toString(),
                    remoteExists && !remoteIsDirectory ? Files.getLastModifiedTime(remote) : null,
                    remoteExists && !remoteIsDirectory ? Files.size(remote) : null,
                    remoteIsDirectory,
                    remoteExists && !remoteIsDirectory
            );

            TreeItem<Item> parent = path.getParent() == null ? rootItem : expandedTree.get(path.getParent());
            TreeItem<Item> item = new TreeItem<>(new Item(localFile, remoteFile));
            parent.getChildren().add(item);
            expandedTree.put(path, item);
        }
        return rootItem;
    }

    private Set<Path> loadAllPaths() throws IOException {
        String ignoreCommon = cfg.getProperty("ignore.common", "$^");
        Pattern ignoreLocal = Pattern.compile("(" + cfg.getProperty("ignore.local", "$^") + ")|(" + ignoreCommon + ")");
        Pattern ignoreRemote = Pattern.compile("(" + cfg.getProperty("ignore.remote", "$^") + ")|(" + ignoreCommon + ")");

        Set<Path> allPaths = new TreeSet<>();
        try (Stream<Path> w1 = Files.walk(localPath); Stream<Path> w2 = Files.walk(remotePath)) {
            w1.filter(p -> !ignoreLocal.matcher(p.toAbsolutePath().normalize().toString()).matches()).forEach(l -> allPaths.add(localPath.relativize(l)));
            w2.filter(p -> !ignoreRemote.matcher(p.toAbsolutePath().normalize().toString()).matches()).forEach(l -> allPaths.add(remotePath.relativize(l)));
        }
        return allPaths;
    }

    private void show(TreeItem<Item> treeItem) {
        if (treeItem == null) {
            return;
        }
        previewLocal.show(treeItem.getValue().local.path);
        previewRemote.show(treeItem.getValue().remote.path);
    }

    private PreviewNode preview() {
        return new PreviewNode();
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

    private static Node downloadIcon() {
        String d = "M8.05,15.15H1.31C.52,15.15.2,14.83.2,14v-2.7a.9.9,0,0,1,1-1H5.27a.57.57,0,0,1,.37.16c.36.34.71.71,1.06,1.06a1.82,1.82,0,0,0,2.68,0c.36-.36.71-.73,1.09-1.08a.61.61,0,0,1,.37-.15h4a.92.92,0,0,1,1,1v2.79a.9.9,0,0,1-1,1Zm3.62-2.4a.6.6,0,1,0,0,1.19.6.6,0,1,0,0-1.19Zm1.82.61a.58.58,0,0,0,.61.58.6.6,0,1,0-.61-.58ZM6.23,5.5v-4c0-.6.2-.79.8-.79H9.11c.53,0,.74.21.75.74,0,1.25,0,2.51,0,3.76,0,.26.07.34.33.33.66,0,1.32,0,2,0a.63.63,0,0,1,.66.39.61.61,0,0,1-.2.71l-4.1,4.09a.6.6,0,0,1-1,0L3.48,6.61a.61.61,0,0,1-.21-.73.63.63,0,0,1,.66-.38h2.3Z";
        SVGPath path = new SVGPath();
        path.setFill(Color.CORNFLOWERBLUE);
        path.setStroke(null);
        path.setContent(d);
        return path;
    }

}