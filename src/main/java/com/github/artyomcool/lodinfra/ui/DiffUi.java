package com.github.artyomcool.lodinfra.ui;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineByte;
import ar.com.hjg.pngj.PngWriter;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;
import com.github.artyomcool.lodinfra.h3common.D32;
import com.github.artyomcool.lodinfra.h3common.Def;
import com.github.artyomcool.lodinfra.h3common.LodFile;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTreeTableView;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.control.SegmentedButton;

import java.io.*;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static javafx.scene.control.TreeTableView.CONSTRAINED_RESIZE_POLICY;

public class DiffUi extends Application {

    private final Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
    private final Insets padding = new Insets(2, 2, 2, 2);

    private final Path localPath;
    private final Path remotePath;
    private final Properties cfg;
    private final Path logs;
    private final String nick;
    private final Executor pollFilesExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    private Consumer<TreeItem<Item>> onFilesChangedAction = t -> {};

    private Stage primaryStage;
    private PreviewNode previewLocal;
    private PreviewNode previewRemote;
    private PreviewNode previewDiff;
    private TreeItem<Item> rootItem;
    private WatchService watchService;

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

    @Override
    public void stop() throws Exception {
        super.stop();
        watchService.close();
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
            lastFiles.setPrefWidth(600);
            previewLocal = preview();
            previewLocal.setAlignment(Pos.CENTER);
            previewRemote = preview();
            previewRemote.setAlignment(Pos.CENTER);
            previewDiff = preview();
            previewRemote.setAlignment(Pos.CENTER);
            VBox previews = new VBox(
                    withLabel(previewLocal, "Preview A"),
                    withLabel(previewRemote, "Perview B"),
                    withLabel(previewDiff, "Perview Diff")
                    );
            ScrollPane pane = new ScrollPane(previews);
            pane.setPrefWidth(400);
            SplitPane box = new SplitPane(lastFiles, pane);
            box.setDividerPosition(0, 0.666);
            root.getChildren().add(box);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());

            primaryStage.setMinWidth(1024);
            primaryStage.setMinHeight(800);
            primaryStage.setScene(scene);
            primaryStage.show();

            watchService = localPath.getFileSystem().newWatchService();
            localPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            remotePath.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            pollFilesExecutor.execute(() -> {
                try {
                    while (true) {
                        WatchKey key = watchService.take();
                        key.pollEvents();
                        key.reset();
                        while ((key = watchService.poll(1, TimeUnit.SECONDS)) != null) {
                            key.pollEvents();
                            key.reset();
                        }
                        TreeItem<Item> item = loadTree();
                        Platform.runLater(() -> onFilesChangedAction.accept(item));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void executeCheckTreeItem(Map<Item, ItemAction> actions, TreeItem<Item> treeItem, boolean checked, ItemAction positive) {
        Item item;
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
        ToggleButton inProgress = new ToggleButton("In Progress");
        SegmentedButton modes = new SegmentedButton(
                fetch,
                observe,
                inProgress,
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
        HBox modesWrapper = new HBox(modes);
        leftPanel.setAlignment(Pos.CENTER_LEFT);
        rightPanel.setAlignment(Pos.CENTER_RIGHT);
        modesWrapper.setAlignment(Pos.CENTER);
        leftPanel.setSpacing(2);
        rightPanel.setSpacing(2);
        leftPanel.setPadding(padding);
        rightPanel.setPadding(padding);
        leftPanel.setMinWidth(120);
        rightPanel.setMinWidth(120);
        modesWrapper.setPadding(padding);
        HBox panelWrapper = new HBox(leftPanel, modesWrapper, rightPanel);
        HBox.setHgrow(modesWrapper, Priority.ALWAYS);
        panelWrapper.setAlignment(Pos.CENTER);

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

                    onFilesChangedAction = treeItem -> {
                        rootItem = treeItem;
                        updateRoot();
                    };
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

            final Button revertButton = new Button("Revert (!)", downloadIcon(Color.DARKRED));
            {
                revertButton.setOnAction(a -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Revert all selected files to remote version?", ButtonType.NO, ButtonType.YES);
                    alert.showAndWait();

                    if (alert.getResult() != ButtonType.YES) {
                        return;
                    }
                    for (Map.Entry<Item, ItemAction> entry : pushActions.entrySet()) {
                        if (entry.getValue() != ItemAction.LOCAL_TO_REMOTE) {
                            continue;
                        }

                        Item item = entry.getKey();
                        if (item.isSynthetic) {
                            continue;
                        }

                        try {
                            if (item.remote.lastModified == null && item.local.isFile) {
                                Files.delete(item.local.path);
                            } else {
                                Files.copy(
                                        item.remote.path,
                                        item.local.path,
                                        StandardCopyOption.REPLACE_EXISTING,
                                        StandardCopyOption.COPY_ATTRIBUTES
                                );
                            }
                        } catch (IOException exception) {
                            exception.printStackTrace();
                        }
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
                    leftPanel.getChildren().setAll(revertButton);
                    rightPanel.getChildren().setAll(pushButton);
                    listWrapper.getChildren().setAll(pushList);

                    onFilesChangedAction = treeItem -> {
                        rootItem = treeItem;
                        updateRoot();
                    };
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
                        updateRoot();
                    }
                    leftPanel.getChildren().clear();
                    rightPanel.getChildren().clear();
                    listWrapper.getChildren().setAll(observeList);

                    onFilesChangedAction = treeItem -> {
                        rootItem = treeItem;
                        updateRoot();
                    };
                }
            }

            private void updateRoot() {
                cachedGlobalRoot = rootItem;
                itemTreeItem = DiffUi.this.filterForObserve();
                observeList.setRoot(itemTreeItem);
            }
        });
        inProgress.selectedProperty().addListener(new ChangeListener<>() {

            private TreeItem<Item> cachedGlobalRoot;
            private TreeItem<Item> itemTreeItem;
            final Map<Item, ItemAction> observeActions = new HashMap<>();
            private final JFXCheckBox checkBox = new JFXCheckBox("Autobuild");
            final JFXTreeTableView<Item> inProgressList = createListComponent(true, false, observeActions);

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    if (cachedGlobalRoot != rootItem) {
                        updateRoot();
                    }
                    leftPanel.getChildren().clear();
                    rightPanel.getChildren().clear();
                    listWrapper.getChildren().setAll(inProgressList);

                    onFilesChangedAction = treeItem -> {
                        rootItem = treeItem;
                        updateRoot();
                    };
                }
            }

            private void updateRoot() {
                cachedGlobalRoot = rootItem;
                itemTreeItem = DiffUi.this.filterForInProgress();

                inProgressList.setRoot(itemTreeItem);

                if (checkBox.isSelected()) {
                    //rebuild();
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

        name.setMinWidth(250);

        list.getColumns().setAll(
                name,
                timeA,
                timeB,
                sizeA,
                sizeB
        );

        Font regular = Font.font("monospace", 12);
        Font bold = Font.font("monospace", FontWeight.BOLD, 12);

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
        sizeA.setCellFactory(new Callback<TreeTableColumn<Item, Long>, TreeTableCell<Item, Long>>() {
            @Override
            public TreeTableCell<Item, Long> call(TreeTableColumn<Item, Long> param) {
                return new TreeTableCell<>() {

                    @Override
                    protected void updateItem(Long text, boolean empty) {
                        super.updateItem(text, empty);
                        super.setGraphic(null);

                        if (empty) {
                            super.setText(null);
                            return;
                        }

                        super.setText(text == null ? "" : text.toString());
                        setFont(regular);
                    }
                };
            }
        });
        sizeB.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getValue().remote.size));
        sizeB.setCellFactory(sizeA.getCellFactory());

        list.setRowFactory(r -> new TreeTableRow<>() {
            {
                MenuItem validate = new MenuItem("Validate");
                Pattern skipFrames = Pattern.compile("0w_.*");
                validate.setOnAction(event -> applyToLeafs(getTreeItem() == null ? rootItem : getTreeItem(), item -> {
                    String name = item.local.path.getFileName().toString().toLowerCase();
                    if (name.endsWith(".def")) {
                        ImgFilesUtils.validateDefNames(item.local.path, skipFrames);
                        ImgFilesUtils.validateDefColors(item.local.path);
                        //ImgFilesUtils.validateDefSpecColors(item.local.path);
                    } else if (name.endsWith(".d32")) {
                        ImgFilesUtils.validateD32Colors(item.local.path);
                    } else if (name.endsWith(".p32")) {
                        ImgFilesUtils.validateP32Colors(item.local.path);
                    } else if (name.endsWith(".lod")) {
                        try {
                            LodFile lod = LodFile.load(item.local.path);
                            for (LodFile.SubFileMeta subFile : lod.subFiles) {
                                if (subFile.nameAsString.toLowerCase().endsWith(".def")) {
                                    Path path = item.local.path.resolveSibling(item.local.path.getFileName() + "?" + subFile.nameAsString);
                                    ImgFilesUtils.validateDefSpecColors(path);
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }));
                MenuItem fixDefNaming = new MenuItem("Fix def naming");
                fixDefNaming.setOnAction(event -> applyToLeafs(getTreeItem(), item -> {
                    String name = item.local.path.getFileName().toString().toLowerCase();
                    if (name.endsWith(".def")) {
                        ImgFilesUtils.fixDefNames(item.local.path, skipFrames);
                    }
                }));
                MenuItem fixColors = new MenuItem("Fix d32/p32 alpha colors");
                fixColors.setOnAction(event -> applyToLeafs(getTreeItem() == null ? rootItem : getTreeItem(), item -> {
                    String name = item.local.path.getFileName().toString().toLowerCase();
                    if (name.endsWith(".d32")) {
                        ImgFilesUtils.fixD32Colors(item.local.path);
                    } else if (name.endsWith(".p32")) {
                        ImgFilesUtils.fixP32Colors(item.local.path);
                    }
                }));
                setContextMenu(new ContextMenu(validate, fixDefNaming, fixColors));
            }

            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    if (!item.local.isDirectory && !item.remote.isDirectory) {
                        setOnMouseClicked(e -> {
                            if (e.getClickCount() == 1 && e.isControlDown()) {
                                try {
                                    Runtime.getRuntime().exec("explorer /select, " + item.local);
                                } catch (IOException ex) {
                                    throw new RuntimeException(ex);
                                }
                                e.consume();
                            } else if (e.getClickCount() == 2) {
                                TreeItem<Item> treeItem = getTreeItem();
                                String itemName = item.local.name.toLowerCase();
                                if (treeItem.getChildren().isEmpty() && (item.local.isFile || item.remote.isFile)) {
                                    if (itemName.endsWith(".lod")) {
                                        expandLod(item, treeItem);
                                    } else if (itemName.endsWith(".d32")) {
                                        unpackD32(item);
                                    } else if (itemName.endsWith(".def")) {
                                        unpackDef(item);
                                    }
                                }
                                e.consume();
                            }
                        });
                    } else {
                        String itemName = item.local.name.toLowerCase();
                        if (itemName.startsWith("[") && itemName.endsWith("]")) {
                            setOnMouseClicked(e -> {
                                if (e.getClickCount() == 1 && e.isControlDown()) {
                                    try {
                                        Runtime.getRuntime().exec("explorer /select, " + item.local);
                                    } catch (IOException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                    e.consume();
                                } else if (e.getClickCount() == 2) {
                                    packDef(item);
                                    e.consume();
                                }
                            });
                        }
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
        list.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY);
        return list;
    }

    private void packDef(Item item) {
        try {
            Path path = item.local.path;
            String name = path.getFileName().toString();
            Path output = path.resolveSibling(name.substring(1, name.length() - 1));
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(path.resolve("def.cfg"), StandardCharsets.UTF_8)) {
                properties.load(reader);
            }

            String format = properties.getProperty("a.format"); // d32
            if (!format.equals("d32")) throw new RuntimeException(format + " is not supported yet");
            int width = Integer.parseInt(properties.getProperty("a.width"));
            int height = Integer.parseInt(properties.getProperty("a.height"));
            int groupsCount = Integer.parseInt(properties.getProperty("a.groups"));

            List<D32.GroupDescriptor> groups = new ArrayList<>(groupsCount);
            Map<String, int[][]> loadedFrames = new HashMap<>();

            for (int i = 0; i < groupsCount; i++) {
                int framesCount = Integer.parseInt(properties.getProperty("group." + i + ".frames"));
                int groupIndex = Integer.parseInt(properties.getProperty("group." + i + ".index"));
                D32.GroupDescriptor group = new D32.GroupDescriptor(groupIndex);
                for (int j = 0; j < framesCount; j++) {
                    D32.FrameDescriptor frame = new D32.FrameDescriptor(
                            properties.getProperty("group." + i + ".frame." + j + ".name"),
                            Integer.parseInt(properties.getProperty("group." + i + ".frame." + j + ".type"))
                    );
                    String file = properties.getProperty("group." + i + ".frame." + j + ".file");
                    loadedFrames.computeIfAbsent(frame.name, k -> ImgFilesUtils.loadD32Frame(path.resolve(file)));

                    group.frameDescriptors.add(frame);
                }
                groups.add(group);
            }

            D32.pack(output, groups, loadedFrames);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path askForUnpack(Item item) throws IOException {
        Path path = getUnpackedPath(item);
        ButtonType justOpen = new ButtonType("Just open");
        ButtonType rewrite = new ButtonType("Rewrite");
        ButtonType unpack = new ButtonType("Unpack");

        Alert dialog;
        if (Files.exists(path)) {
            dialog = new Alert(
                    Alert.AlertType.WARNING,
                    "This d32 already unpacked. Rewrite?",
                    justOpen,
                    rewrite,
                    ButtonType.CANCEL
            );
        } else {
            dialog = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Unpack this d32?",
                    unpack,
                    ButtonType.CANCEL
            );
        }

        Optional<ButtonType> shown = dialog.showAndWait();
        if (shown.isEmpty()) {
            return null;
        }
        ButtonType buttonType = shown.get();
        if (buttonType.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
            return null;
        }
        if (buttonType == rewrite) {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return this.visitFile(dir, null);
                }
            });
        }
        if (buttonType == unpack || buttonType == rewrite) {
            try {
                Files.createDirectory(path);
            } catch (IOException ex) {
                ex.printStackTrace();
                return null;
            }
        }
        return buttonType == unpack || buttonType == rewrite ? path : null;
    }

    private static Path getUnpackedPath(Item item) {
        return item.local.path.resolveSibling("[" + item.local.path.getFileName() + "]");
    }

    private void unpackD32(Item item) {
        Path path;
        try {
            path = askForUnpack(item);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (path == null) {
            getHostServices().showDocument(getUnpackedPath(item).toString());
            return;
        }

        boolean isPng;
        boolean useGroups;
        {
            ButtonType png = new ButtonType("PNG");
            ButtonType bmp = new ButtonType("BMP");
            Alert dialog = new Alert(
                    Alert.AlertType.NONE,
                    "Choose format",
                    png,
                    bmp
            );

            Optional<ButtonType> dialogResult = dialog.showAndWait();
            if (dialogResult.isEmpty()) {
                return;
            }
            isPng = dialogResult.get() == png;
        }
        {
            Alert dialog = new Alert(
                    Alert.AlertType.NONE,
                    "Use groups in file names?",
                    ButtonType.YES,
                    ButtonType.NO
            );

            Optional<ButtonType> dialogResult = dialog.showAndWait();
            if (dialogResult.isEmpty()) {
                return;
            }
            useGroups = dialogResult.get() == ButtonType.YES;
        }

        Path p = item.local.path;
        ImgFilesUtils.processFile(p, null, buffer -> {
            D32 d32 = new D32(p.toString(), buffer);
            String[] groupNames;
            if (d32.groups.size() >= 22) {
                groupNames = new String[]{
                        "Moving",
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
            } else if (d32.groups.size() >= 10) {
                groupNames = new String[]{
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
            } else if (d32.groups.size() >= 5) {
                groupNames = new String[]{
                        "Standing",
                        "Shuffle",
                        "Failure",
                        "Victory",
                        "Cast-Spell",
                };
            } else {
                groupNames = new String[]{};
            }

            Map<Image, List<D32.Frame>> imageToFrames = new LinkedHashMap<>();
            Map<D32.Frame, Image> imageMap = ImgFilesUtils.loadD32(d32, true, true);
            for (Map.Entry<D32.Frame, Image> entry : imageMap.entrySet()) {
                imageToFrames.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
            }
            Map<D32.Frame, String> names = new LinkedHashMap<>();

            for (Map.Entry<Image, List<D32.Frame>> entry : imageToFrames.entrySet()) {
                Image image = entry.getKey();
                List<D32.Frame> frames = entry.getValue();

                D32.Frame mainFrame = frames.get(0);
                String extra = "";
                if (frames.size() > 1) {
                    StringBuilder extraBuilder = new StringBuilder();
                    for (int i = 1; i < frames.size(); i++) {
                        D32.Frame frame = frames.get(i);
                        extraBuilder.append("_")
                                .append(frame.group.index)
                                .append(".")
                                .append(frame.index);
                    }
                    extra = extraBuilder.toString();
                }
                D32.Group group = mainFrame.group;

                String groupName = groupNames.length > group.index ? (groupNames[group.index] + "_") : "";
                String fileName;
                if (useGroups) {
                    fileName = String.format(
                            "[G_%03d_%s%03d%s] %s." + (isPng ? "png" : "bmp"),
                            group.index,
                            groupName,
                            mainFrame.index,
                            extra,
                            mainFrame.name
                    );
                } else {
                    fileName = mainFrame.name + (isPng ? ".png" : ".bmp");
                }

                for (D32.Frame frame : frames) {
                    names.put(frame, fileName);
                }

                if (isPng) {
                    try (OutputStream out = Files.newOutputStream(path.resolve(fileName))){
                        ImageInfo header = new ImageInfo((int) image.getWidth(), (int) image.getHeight(), 8, true, false, false);
                        PngWriter pngWriter = new PngWriter(out, header);

                        for (int y = 0; y < image.getHeight(); y++) {
                            ImageLineByte line = new ImageLineByte(header);
                            for (int x = 0; x < image.getWidth(); x++) {
                                int color = image.getPixelReader().getArgb(x, y);
                                line.getScanline()[x * 4] = (byte) (color >>> 0);
                                line.getScanline()[x * 4 + 1] = (byte) (color >>> 8);
                                line.getScanline()[x * 4 + 2] = (byte) (color >>> 16);
                                line.getScanline()[x * 4 + 3] = (byte) (color >>> 24);
                            }
                            pngWriter.writeRow(line);
                        }
                        pngWriter.end();
                    }
                } else {
                    int[][] decode = d32.decode(mainFrame);
                    ImgFilesUtils.d32ToPcxColors(decode, true);
                    ImgFilesUtils.writeBmp(path.resolve(fileName), decode);
                }
            }

            Properties properties = new Properties() {
                @Override
                public Set<Map.Entry<Object, Object>> entrySet() {
                    final TreeSet<Map.Entry<Object, Object>> entries = new TreeSet<>((o1, o2) -> {
                        return ((Comparable) o1.getKey()).compareTo(o2.getKey());
                    });
                    entries.addAll(super.entrySet());
                    return entries;
                }
            };
            properties.setProperty("a.format", "d32");
            properties.setProperty("a.width", d32.fullWidth + "");
            properties.setProperty("a.height", d32.fullHeight + "");
            properties.setProperty("a.groups", d32.groups.size() + "");

            int i = 0;
            for (D32.Group group : d32.groups) {
                properties.setProperty("group." + i + ".frames", group.frames.size() + "");
                properties.setProperty("group." + i + ".index", group.index + "");
                for (D32.Frame frame : group.frames) {
                    properties.setProperty("group." + i + ".frame." + frame.index + ".file", names.get(frame));
                    properties.setProperty("group." + i + ".frame." + frame.index + ".name", frame.name);
                    properties.setProperty("group." + i + ".frame." + frame.index + ".type", String.valueOf(frame.frameDrawType));
                }
                i++;
            }

            try (Writer writer = Files.newBufferedWriter(path.resolve("def.cfg"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(writer, "Def config");
            }
            return null;
        });

        getHostServices().showDocument(path.toString());
    }

    private void unpackDef(Item item) {
        Path path = getUnpackedPath(item);
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
                return;
            }
        }
        if (buttonType == unpack || buttonType == rewrite) {
            Path p = item.local.path;
            ImgFilesUtils.processFile(p, null, buffer -> {
                Def def = new Def(p.toString(), buffer);
                String[] groupNames = switch (def.type) {
                    case 0x42 -> new String[]{
                            "Moving",
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
                Map<Def.Frame, String> names = new LinkedHashMap<>();

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
                    for (Def.Frame frame : frames) {
                        names.put(frame, fileName);
                    }

                    try (OutputStream out = Files.newOutputStream(path.resolve(fileName))){
                        ImageInfo header = new ImageInfo((int)image.getWidth(), (int)image.getHeight(), 8, false, false, true);
                        PngWriter pngWriter = new PngWriter(out, header);
                        PngChunkPLTE plteChunk = pngWriter.getMetadata().createPLTEChunk();
                        plteChunk.setNentries(256);
                        Map<Integer, Byte> colors = new HashMap<>();
                        for (int i = 0; i < 256; i++) {
                            int c = def.palette[i];
                            int r1 = (c >>> 16) & 0xff;
                            int g = (c >>> 8) & 0xff;
                            int b = c & 0xff;
                            plteChunk.setEntry(i, r1, g, b);
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

                Properties properties = new Properties() {
                    @Override
                    public Set<Map.Entry<Object, Object>> entrySet() {
                        final TreeSet<Map.Entry<Object, Object>> entries = new TreeSet<>((o1, o2) -> {
                            return ((Comparable) o1.getKey()).compareTo(o2.getKey());
                        });
                        entries.addAll(super.entrySet());
                        return entries;
                    }
                };
                properties.setProperty("a.format", "def");
                properties.setProperty("a.type", def.type + "");
                properties.setProperty("a.width", def.fullWidth + "");
                properties.setProperty("a.height", def.fullHeight + "");
                properties.setProperty("a.groups", def.groups.size() + "");

                for (Def.Group group : def.groups) {
                    properties.setProperty("group." + group.index + ".frames", group.frames.size() + "");
                    for (Def.Frame frame : group.frames) {
                        properties.setProperty("group." + group.index + ".frame." + frame.index + ".file", names.get(frame));
                        properties.setProperty("group." + group.index + ".frame." + frame.index + ".name", frame.name);
                        properties.setProperty("group." + group.index + ".frame." + frame.index + ".compression", frame.compression + "");
                    }
                }

                try (Writer writer = Files.newBufferedWriter(path.resolve("def.cfg"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    properties.store(writer, "Def config");
                }
                return null;
            });
        }

        getHostServices().showDocument(path.toString());
    }

    private void expandLod(Item item, TreeItem<Item> treeItem) {
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

    private TreeItem<Item> filterForInProgress() {
        Predicate<TreeItem<Item>> preFilter = item -> true;
        Function<TreeItem<Item>, TreeItem<Item>> fold = item -> {
            if (item.getChildren().isEmpty()) {
                if (!item.getValue().local.name.startsWith("[")
                        || !item.getValue().local.name.endsWith("]")
                        || item.getValue().local.isFile
                        || item.getValue().remote.isFile
                ) {
                    return null;
                }
            }
            item.setExpanded(true);
            return item;
        };
        TreeItem<Item> filter = filter(rootItem, preFilter, fold);
        return filter == null ? new TreeItem<>(rootItem.getValue()) : filter;
    }

    private TreeItem<Item> filterForFetch() {
        Predicate<TreeItem<Item>> preFilter = item -> {
            if (!item.getChildren().isEmpty()) {
                return !item.getValue().local.name.startsWith("[");
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
                return !item.getValue().local.name.startsWith("[");
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
        boolean isRoot = item == rootItem;
        item = new TreeItem<>(item.getValue());

        for (TreeItem<Item> child : children) {
            TreeItem<Item> clone = filter(child, preFilter, fold);
            if (clone != null) {
                item.getChildren().add(clone);
            }
        }

        return isRoot ? item : fold.apply(item);
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
                ImgFilesUtils.validateDefNames(localPath.resolve(path), skipFrames);
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
        previewDiff.show(treeItem.getValue().local.path, treeItem.getValue().remote.path);

        PreviewNode[] node = new PreviewNode[1];

        MenuItem showIssues = new MenuItem("Show issues");
        showIssues.setOnAction(a -> {
            node[0].postProcess(image -> {
                int multiply = 8;
                int width = (int) image.getWidth();
                int height = (int) image.getHeight();
                WritableImage result = new WritableImage(width * multiply, height * multiply);
                int[] scanline = new int[(int) result.getWidth()];
                for (int y = 0, dy = 0; y < height; y++) {
                    WritablePixelFormat<IntBuffer> format = PixelFormat.getIntArgbInstance();
                    image.getPixelReader().getPixels(0, y, width, 1, format, scanline, 0, width);
                    for (int src = width - 1, dst = scanline.length - 1; src >= 0; src--) {
                        boolean invalid = ImgFilesUtils.invalidColorDiff(ImgFilesUtils.colorDifFromStd(scanline[src]));
                        for (int i = 0; i < multiply; i++, dst--) {
                            scanline[dst] = scanline[src];
                        }
                        if (invalid) {
                            scanline[dst + multiply] = 0xff000000;
                            scanline[dst + 1] = 0xff000000;
                        }
                    }
                    dy++;
                    for (int i = 0; i < multiply - 2; i++, dy++) {
                        result.getPixelWriter().setPixels(0, dy, scanline.length, 1, format, scanline, 0, scanline.length);
                    }
                    for (int i = 0; i < scanline.length; i++) {
                        boolean invalid = ImgFilesUtils.invalidColorDiff(ImgFilesUtils.colorDifFromStd(scanline[i]));
                        if (invalid) {
                            scanline[i] = 0xff000000;
                        }
                    }
                    result.getPixelWriter().setPixels(0, dy++, scanline.length, 1, format, scanline, 0, scanline.length);
                    result.getPixelWriter().setPixels(0, dy - multiply, scanline.length, 1, format, scanline, 0, scanline.length);
                }
                return result;
            });
        });

        MenuItem showFrame = new MenuItem("Show frame");
        showFrame.setOnAction(a -> {
            TextInputDialog dialog = new TextInputDialog("Frame number");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(s -> node[0].showFrame(Integer.parseInt(s)));
        });
        ContextMenu menu = new ContextMenu(showIssues, showFrame);

        EventHandler<ContextMenuEvent> handler = e -> {
            node[0] = (PreviewNode) e.getSource();
            menu.show(node[0], e.getScreenX(), e.getScreenY());
        };
        previewLocal.setOnContextMenuRequested(handler);
        previewRemote.setOnContextMenuRequested(handler);
        previewDiff.setOnContextMenuRequested(handler);
    }

    public void applyToLeafs(TreeItem<Item> treeItem, Consumer<Item> consumer) {
        if (treeItem.isLeaf()) {
            consumer.accept(treeItem.getValue());
        } else {
            for (TreeItem<Item> child : treeItem.getChildren()) {
                applyToLeafs(child, consumer);
            }
        }
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
        return downloadIcon(Color.CORNFLOWERBLUE);
    }

    private static Node downloadIcon(Color color) {
        String d = "M8.05,15.15H1.31C.52,15.15.2,14.83.2,14v-2.7a.9.9,0,0,1,1-1H5.27a.57.57,0,0,1,.37.16c.36.34.71.71,1.06,1.06a1.82,1.82,0,0,0,2.68,0c.36-.36.71-.73,1.09-1.08a.61.61,0,0,1,.37-.15h4a.92.92,0,0,1,1,1v2.79a.9.9,0,0,1-1,1Zm3.62-2.4a.6.6,0,1,0,0,1.19.6.6,0,1,0,0-1.19Zm1.82.61a.58.58,0,0,0,.61.58.6.6,0,1,0-.61-.58ZM6.23,5.5v-4c0-.6.2-.79.8-.79H9.11c.53,0,.74.21.75.74,0,1.25,0,2.51,0,3.76,0,.26.07.34.33.33.66,0,1.32,0,2,0a.63.63,0,0,1,.66.39.61.61,0,0,1-.2.71l-4.1,4.09a.6.6,0,0,1-1,0L3.48,6.61a.61.61,0,0,1-.21-.73.63.63,0,0,1,.66-.38h2.3Z";
        SVGPath path = new SVGPath();
        path.setFill(color);
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