package com.github.artyomcool.lodinfra.ui;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineByte;
import ar.com.hjg.pngj.PngWriter;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;
import com.github.artyomcool.lodinfra.h3common.Def;
import com.github.artyomcool.lodinfra.h3common.LodFile;
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
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.control.SegmentedButton;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
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
    private final Path logs;
    private final String nick;

    private Stage primaryStage;
    private PreviewNode previewLocal;
    private PreviewNode previewRemote;
    private TreeItem<Item> rootItem;

    public DiffUi(Path localPath, Path remotePath, Path cfg, Path logs, String nick) {
        this.localPath = localPath.toAbsolutePath();
        this.remotePath = remotePath.toAbsolutePath();
        this.logs = logs;
        this.nick = nick;

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

    private void executeCheckTreeItem(Map<Item, ItemAction> actions, TreeItem<Item> treeItem, boolean checked, ItemAction positive) {
        Item item = treeItem.getValue();
        if (checked) {
            markWithChildren(actions, treeItem, positive);
            a:
            while (treeItem.getParent() != null) {
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
            markWithChildren(actions, treeItem, ItemAction.NOTHING);
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

    private void markWithChildren(Map<Item, ItemAction> actions, TreeItem<Item> treeItem, ItemAction action) {
        Item item = treeItem.getValue();
        if (actions.put(item, action) != action) {
            Event.fireEvent(treeItem, new TreeItem.TreeModificationEvent<>(TreeItem.valueChangedEvent(), treeItem, item));
        }
        for (TreeItem<Item> child : treeItem.getChildren()) {
            markWithChildren(actions, child, action);
        }
    }

    private VBox files(StackPane root) throws IOException {
        ToggleButton fetch = new ToggleButton("Fetch");
        fetch.setGraphic(downloadIcon());
        ToggleButton observe = new ToggleButton("Observe");
        ToggleButton push = new ToggleButton("Push");
        push.setGraphic(uploadIcon());
        SegmentedButton modes = new SegmentedButton(
                fetch,
                observe,
                push
        );
        modes.getToggleGroup().selectedToggleProperty().addListener((obsVal, oldVal, newVal) -> {
            if (newVal == null) {
                oldVal.setSelected(true);
            }
        });

        rootItem = loadTree();

        HBox leftPanel = new HBox();
        HBox rightPanel = new HBox();
        leftPanel.setAlignment(Pos.CENTER_LEFT);
        rightPanel.setAlignment(Pos.CENTER_RIGHT);
        TilePane panelWrapper = new TilePane();
        panelWrapper.setPrefColumns(3);
        panelWrapper.setAlignment(Pos.CENTER);
        TilePane.setAlignment(leftPanel, Pos.CENTER_LEFT);
        TilePane.setAlignment(rightPanel, Pos.CENTER_RIGHT);
        panelWrapper.getChildren().setAll(leftPanel, modes, rightPanel);

        StackPane listWrapper = new StackPane();
        VBox listParent = new VBox(panelWrapper, listWrapper);
        listParent.setFillWidth(true);
        VBox.setMargin(panelWrapper, padding);
        VBox.setVgrow(listWrapper, Priority.ALWAYS);

        fetch.selectedProperty().addListener(new ChangeListener<>() {

            private TreeItem<Item> cachedGlobalRoot;
            private TreeItem<Item> itemTreeItem;

            final Map<Item, ItemAction> fetchActions = new HashMap<>();
            final JFXTreeTableView<Item> fetchList = createListComponent(false, false, fetchActions);

            final Button fetchButton = new Button("Fetch selected", downloadIcon());

            {
                fetchButton.setOnAction(a -> {
                    for (Map.Entry<Item, ItemAction> entry : fetchActions.entrySet()) {
                        if (entry.getValue() != ItemAction.REMOTE_TO_LOCAL) {
                            continue;
                        }

                        Item item = entry.getKey();
                        if (item.isSynthetic || !item.remote.isFile) {
                            continue;
                        }
                        try {
                            Files.createDirectories(item.local.path.getParent());
                            Files.copy(
                                    item.remote.path,
                                    item.local.path,
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.COPY_ATTRIBUTES
                            );
                        } catch (IOException exception) {
                            exception.printStackTrace();
                        }
                    }
                    fetchActions.clear();
                    try {
                        rootItem = loadTree();
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }

                    updateRoot();
                });
            }

            private void updateRoot() {
                cachedGlobalRoot = rootItem;
                itemTreeItem = DiffUi.this.filterForFetch();
                fetchList.setRoot(itemTreeItem);
            }

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    if (cachedGlobalRoot != rootItem) {
                        updateRoot();
                        executeCheckTreeItem(fetchActions, itemTreeItem, true, ItemAction.REMOTE_TO_LOCAL);
                    }

                    leftPanel.getChildren().setAll(fetchButton);
                    rightPanel.getChildren().clear();
                    listWrapper.getChildren().setAll(fetchList);
                }
            }
        });
        push.selectedProperty().addListener(new ChangeListener<>() {

            private DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                    .appendValue(ChronoField.YEAR, 4)
                    .appendLiteral('-')
                    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
                    .appendLiteral('-')
                    .appendValue(ChronoField.DAY_OF_MONTH, 2)
                    .appendLiteral('.')
                    .appendValue(ChronoField.HOUR_OF_DAY, 2)
                    .appendLiteral('-')
                    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                    .appendLiteral('-')
                    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                    .appendLiteral('.')
                    .appendLiteral(nick)
                    .appendLiteral(".txt")
                    .toFormatter();

            private TreeItem<Item> cachedGlobalRoot;
            private TreeItem<Item> itemTreeItem;
            final Map<Item, ItemAction> pushActions = new HashMap<>();
            final JFXTreeTableView<Item> pushList = createListComponent(false, true, pushActions);

            final Button pushButton = new Button("Push selected", uploadIcon());

            {

                pushButton.setOnAction(a -> {
                    StringBuilder files = new StringBuilder();
                    for (Map.Entry<Item, ItemAction> entry : pushActions.entrySet()) {
                        if (entry.getValue() != ItemAction.LOCAL_TO_REMOTE) {
                            continue;
                        }

                        Item item = entry.getKey();
                        if (item.isSynthetic || !item.remote.isFile) {
                            continue;
                        }

                        files.append(item.remote.name).append("\r\n");
                    }
                    files.append("\r\n");

                    String text = new TextAreaDialog("Push description", "General description:\r\n\r\nChanged:\r\n" + files).showForText();
                    if (text == null) {
                        return;
                    }

                    for (Map.Entry<Item, ItemAction> entry : pushActions.entrySet()) {
                        if (entry.getValue() != ItemAction.LOCAL_TO_REMOTE) {
                            continue;
                        }

                        Item item = entry.getKey();
                        if (item.isSynthetic || !item.remote.isFile) {
                            continue;
                        }

                        try {
                            Files.createDirectories(item.remote.path.getParent());
                            Files.copy(
                                    item.local.path,
                                    item.remote.path,
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.COPY_ATTRIBUTES
                            );
                        } catch (IOException exception) {
                            exception.printStackTrace();

                            StringWriter sw = new StringWriter();
                            PrintWriter pw = new PrintWriter(sw);
                            exception.printStackTrace(pw);
                            text = text + "\r\n" + sw;
                        }
                    }

                    try {
                        Files.createDirectories(logs);
                        Files.writeString(logs.resolve(LocalDateTime.now(ZoneOffset.UTC).format(dtf)), text);
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }

                    pushActions.clear();
                    try {
                        rootItem = loadTree();
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }

                    updateRoot();
                });
            }

            private void updateRoot() {
                cachedGlobalRoot = rootItem;
                itemTreeItem = DiffUi.this.filterForPush();
                pushList.setRoot(itemTreeItem);
            }

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    if (cachedGlobalRoot != rootItem) {
                        updateRoot();
                    }
                    leftPanel.getChildren().clear();
                    rightPanel.getChildren().setAll(pushButton);
                    listWrapper.getChildren().setAll(pushList);
                }
            }
        });
        observe.selectedProperty().addListener(new ChangeListener<>() {

            private TreeItem<Item> cachedGlobalRoot;
            private TreeItem<Item> itemTreeItem;
            final Map<Item, ItemAction> observeActions = new HashMap<>();
            final JFXTreeTableView<Item> observeList = createListComponent(true, false, observeActions);

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    if (cachedGlobalRoot != rootItem) {
                        cachedGlobalRoot = rootItem;
                        itemTreeItem = DiffUi.this.filterForObserve();
                        observeList.setRoot(itemTreeItem);
                    }
                    leftPanel.getChildren().clear();
                    rightPanel.getChildren().clear();
                    listWrapper.getChildren().setAll(observeList);
                }
            }
        });

        fetch.fire();

        return listParent;
    }

    private JFXTreeTableView<Item> createListComponent(boolean observe, boolean push, Map<Item, ItemAction> actions) {
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

        Font regular = Font.font(12);
        Font bold = Font.font(null, FontWeight.BOLD, 12);

        ItemAction positiveAction = push ? ItemAction.LOCAL_TO_REMOTE : ItemAction.REMOTE_TO_LOCAL;
        ItemAction negativeAction = push ? ItemAction.REMOTE_TO_LOCAL : ItemAction.LOCAL_TO_REMOTE;

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
                            executeCheckTreeItem(actions, treeItem, newValue, positiveAction);
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
                                if (observe) {
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

                                    if (action == negativeAction) {
                                        setTextFill(Color.RED);
                                        setFont(bold);
                                    } else if (action == positiveAction) {
                                        setTextFill(Color.DARKBLUE);
                                        setFont(bold);
                                    } else {
                                        setTextFill(Color.BLACK);
                                        setFont(regular);
                                    }

                                    if (!item.isSynthetic) {
                                        boolean old = ignoreUpdate;
                                        ignoreUpdate = true;
                                        checkBox.setSelected(action != ItemAction.NOTHING);
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
                                    String itemName = item.local.name.toLowerCase();
                                    if (itemName.endsWith(".lod")) {
                                        TreeSet<String> allResources = new TreeSet<>();
                                        Map<String, LodFile.SubFileMeta> localMapping = new HashMap<>();
                                        Map<String, LodFile.SubFileMeta> remoteMapping = new HashMap<>();

                                        try {
                                            if (item.local.isFile) {
                                                LodFile lod = LodFile.load(item.local.path);
                                                for (LodFile.SubFileMeta subFile : lod.subFiles) {
                                                    String name = subFile.nameAsString.toLowerCase();
                                                    allResources.add(name);
                                                    localMapping.put(name, subFile);
                                                }
                                            }
                                            if (item.remote.isFile) {
                                                LodFile lod = LodFile.load(item.remote.path);
                                                for (LodFile.SubFileMeta subFile : lod.subFiles) {
                                                    String name = subFile.nameAsString.toLowerCase();
                                                    allResources.add(name);
                                                    remoteMapping.put(name, subFile);
                                                }
                                            }

                                            List<TreeItem<Item>> items = new ArrayList<>();
                                            for (String res : allResources) {
                                                LodFile.SubFileMeta localMeta = localMapping.get(res);
                                                LodFile.SubFileMeta remoteMeta = remoteMapping.get(res);

                                                String localName = localMeta == null
                                                        ? remoteMeta.nameAsString
                                                        : localMeta.nameAsString;

                                                String remoteName = remoteMeta == null
                                                        ? localMeta.nameAsString
                                                        : remoteMeta.nameAsString;


                                                FileInfo localFile = new FileInfo(
                                                        item.local.path.resolveSibling(item.local.path.getFileName() + "?" + localName),
                                                        localName,
                                                        null,
                                                        localMeta == null ? null : (long) localMeta.uncompressedSize,
                                                        false,
                                                        localMeta != null
                                                );
                                                FileInfo remoteFile = new FileInfo(
                                                        item.remote.path.resolveSibling(item.remote.path.getFileName() + "?" + remoteName),
                                                        remoteName,
                                                        null,
                                                        remoteMeta == null ? null : (long) remoteMeta.uncompressedSize,
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
                                    } else if (itemName.endsWith(".def")) {
                                        Path path = item.local.path.resolveSibling("[" + item.local.path.getFileName() + "]");
                                        ButtonType justOpen = new ButtonType("Just open");
                                        ButtonType rewrite = new ButtonType("Rewrite");
                                        ButtonType unpack = new ButtonType("Unpack");

                                        Alert dialog;
                                        if (Files.exists(path)) {
                                            dialog = new Alert(
                                                    Alert.AlertType.WARNING,
                                                    "This def already unpacked. Rewrite?",
                                                    justOpen,
                                                    rewrite,
                                                    ButtonType.CANCEL
                                            );
                                        } else {
                                            dialog = new Alert(
                                                    Alert.AlertType.CONFIRMATION,
                                                    "Unpack this def?",
                                                    unpack,
                                                    ButtonType.CANCEL
                                            );
                                        }

                                        Optional<ButtonType> shown = dialog.showAndWait();
                                        if (shown.isEmpty()) {
                                            return;
                                        }
                                        ButtonType buttonType = shown.get();
                                        if (buttonType.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                                            return;
                                        }

                                        if (buttonType == unpack) {
                                            try {
                                                Files.createDirectory(path);
                                            } catch (IOException ex) {
                                                ex.printStackTrace();
                                            }
                                        }
                                        if (buttonType == unpack || buttonType == rewrite) {
                                            Path p = item.local.path;
                                            try (FileChannel channel = FileChannel.open(p)) {
                                                MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, Files.size(p));
                                                Def def = new Def(p.toString(), buffer);
                                                String[] groupNames = switch (def.type) {
                                                    case 0x42 -> new String[]{"Moving",
                                                            "Mouse-Over",
                                                            "Standing",
                                                            "Getting-Hit",
                                                            "Defend",
                                                            "Death",
                                                            "Unused-Death",
                                                            "Turn-Left",
                                                            "Turn-Right",
                                                            "Turn-Left",
                                                            "Turn-Right",
                                                            "Attack-Up",
                                                            "Attack-Straight",
                                                            "Attack-Down",
                                                            "Shoot-Up",
                                                            "Shoot-Straight",
                                                            "Shoot-Down",
                                                            "2-Hex-Attack-Up",
                                                            "2-Hex-Attack-Straight",
                                                            "2-Hex-Attack-Down",
                                                            "Start-Moving",
                                                            "Stop-Moving",
                                                    };
                                                    case 0x44 -> new String[]{
                                                            "Up",
                                                            "Up-Right",
                                                            "Right",
                                                            "Down-Right",
                                                            "Down",
                                                            "Move-Up",
                                                            "Move-Up-Right",
                                                            "Move-Right",
                                                            "Move-Down-Right",
                                                            "Move-Down",
                                                    };
                                                    case 0x49 -> new String[]{
                                                            "Standing",
                                                            "Shuffle",
                                                            "Failure",
                                                            "Victory",
                                                            "Cast-Spell",
                                                    };
                                                    default -> new String[]{};
                                                };

                                                Map<Image, List<Def.Frame>> imageToFrames = new LinkedHashMap<>();
                                                Map<Def.Frame, Image> imageMap = ImgFilesUtils.loadDef(def);
                                                for (Map.Entry<Def.Frame, Image> entry : imageMap.entrySet()) {
                                                    imageToFrames.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
                                                }

                                                for (Map.Entry<Image, List<Def.Frame>> entry : imageToFrames.entrySet()) {
                                                    Image image = entry.getKey();
                                                    List<Def.Frame> frames = entry.getValue();

                                                    Def.Frame mainFrame = frames.get(0);
                                                    String extra = "";
                                                    if (frames.size() > 1) {
                                                        StringBuilder extraBuilder = new StringBuilder();
                                                        for (int i = 1; i < frames.size(); i++) {
                                                            Def.Frame frame = frames.get(i);
                                                            extraBuilder.append("_")
                                                                    .append(frame.group.index)
                                                                    .append(".")
                                                                    .append(frame.index);
                                                        }
                                                        extra = extraBuilder.toString();
                                                    }
                                                    Def.Group group = mainFrame.group;

                                                    String groupName = groupNames.length > group.index ? (groupNames[group.index] + "_") : "";
                                                    String fileName = String.format("[G_%03d_%s%03d%s] %s.png", group.index, groupName, mainFrame.index, extra, mainFrame.name);

                                                    try (OutputStream out = Files.newOutputStream(path.resolve(fileName))){
                                                        ImageInfo header = new ImageInfo((int)image.getWidth(), (int)image.getHeight(), 8, false, false, true);
                                                        PngWriter pngWriter = new PngWriter(out, header);
                                                        PngChunkPLTE plteChunk = pngWriter.getMetadata().createPLTEChunk();
                                                        plteChunk.setNentries(256);
                                                        Map<Integer, Byte> colors = new HashMap<>();
                                                        for (int i = 0; i < 256; i++) {
                                                            int c = def.palette[i];
                                                            int r = (c >>> 16) & 0xff;
                                                            int g = (c >>> 8) & 0xff;
                                                            int b = c & 0xff;
                                                            plteChunk.setEntry(i, r, g, b);
                                                            colors.put(c, (byte)i);
                                                        }

                                                        for (int y = 0; y < image.getHeight(); y++) {
                                                            ImageLineByte line = new ImageLineByte(header);
                                                            for (int x = 0; x < image.getWidth(); x++) {
                                                                int color = image.getPixelReader().getArgb(x, y);
                                                                line.getScanline()[x] = color >>> 24 == 0 ? 0 : colors.get(color);
                                                            }
                                                            pngWriter.writeRow(line);
                                                        }
                                                        pngWriter.end();
                                                    }
                                                }


                                            } catch (Exception ex) {
                                                ex.printStackTrace();
                                                return;
                                            }
                                        }

                                        getHostServices().showDocument(path.toString());
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
            if (observe || !" ".equals(k.getCharacter())) {
                return;
            }
            TreeItem<Item> treeItem = list.getSelectionModel().getSelectedItem();
            if (treeItem == null) {
                return;
            }
            Item item = treeItem.getValue();
            ItemAction a = actions.getOrDefault(item, ItemAction.NOTHING);
            ItemAction target = push ? ItemAction.LOCAL_TO_REMOTE : ItemAction.REMOTE_TO_LOCAL;
            executeCheckTreeItem(actions, treeItem, a != target, target);
        });
        list.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                show(list.getTreeItem(newValue.intValue()));
            }
        });

        list.setShowRoot(false);
        list.setFixedCellSize(20);
        return list;
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
        TreeItem<Item> filter = filter(rootItem, preFilter, fold);
        return filter == null ? new TreeItem<>(rootItem.getValue()) : filter;
    }

    private TreeItem<Item> filterForFetch() {
        Predicate<TreeItem<Item>> preFilter = item -> {
            if (!item.getChildren().isEmpty()) {
                return true;
            }
            return item.getValue().status == ItemStatus.REMOTE_NEWER;
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
        TreeItem<Item> filter = filter(rootItem, preFilter, fold);
        return filter == null ? new TreeItem<>(rootItem.getValue()) : filter;
    }

    private TreeItem<Item> filterForPush() {
        Predicate<TreeItem<Item>> preFilter = item -> {
            if (!item.getChildren().isEmpty()) {
                return true;
            }
            return item.getValue().status == ItemStatus.LOCAL_NEWER;
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
        TreeItem<Item> filter = filter(rootItem, preFilter, fold);
        return filter == null ? new TreeItem<>(rootItem.getValue()) : filter;
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

        List<Path> localPaths = new ArrayList<>();
        List<Path> remotePaths = new ArrayList<>();
        try (Stream<Path> w1 = Files.walk(localPath); Stream<Path> w2 = Files.walk(remotePath)) {
            w1.filter(p -> !ignoreLocal.matcher(p.toAbsolutePath().normalize().toString()).matches()).forEach(l -> localPaths.add(localPath.relativize(l)));
            w2.filter(p -> !ignoreRemote.matcher(p.toAbsolutePath().normalize().toString()).matches()).forEach(l -> remotePaths.add(remotePath.relativize(l)));
        }

        scanLocal(localPaths, Pattern.compile("0w_.*"));

        Set<Path> allPaths = new TreeSet<>();
        allPaths.addAll(remotePaths);
        allPaths.addAll(localPaths);
        return allPaths;
    }

    private void scanLocal(List<Path> localPaths, Pattern skipFrames) {
        if (true) {
            return;
        }
        for (Path path : localPaths) {
            String name = path.getFileName().toString().toLowerCase();
            if (name.endsWith(".def")) {
                ImgFilesUtils.validateDef(localPath.resolve(path), skipFrames);
            } else if (name.endsWith(".lod")) {

            }
        }
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
        LOCAL_NEWER,
        REMOTE_NEWER,
        CONFLICT
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

    private static Node uploadIcon() {
        String d = "M16,9.64v.74a.54.54,0,0,0,0,.1,3.55,3.55,0,0,1-.46,1.35,3.68,3.68,0,0,1-3.35,1.89H3.37a3.49,3.49,0,0,1-1-.13A3.21,3.21,0,0,1,.07,10,3.13,3.13,0,0,1,1.51,7.78a.19.19,0,0,0,.1-.26,2,2,0,0,1,.1-1.43A2.2,2.2,0,0,1,4.3,4.82a.16.16,0,0,0,.22-.1,3.13,3.13,0,0,1,.19-.36A4.46,4.46,0,0,1,9.79,2.43a4.38,4.38,0,0,1,3.14,3.69c0,.16.1.2.23.24A3.63,3.63,0,0,1,15.8,8.79,5.85,5.85,0,0,1,16,9.64ZM7,9.18v1.94a.5.5,0,0,0,.55.56h.89A.5.5,0,0,0,9,11.12V9.36c0-.06,0-.11,0-.18h1a.48.48,0,0,0,.45-.29.49.49,0,0,0-.12-.53l-2-1.93a.49.49,0,0,0-.77,0l-2,1.93a.49.49,0,0,0-.12.53A.49.49,0,0,0,6,9.17H7Z";
        SVGPath path = new SVGPath();
        path.setFill(Color.GOLDENROD);
        path.setStroke(null);
        path.setContent(d);
        return path;
    }

}