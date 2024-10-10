package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.Icons;
import com.github.artyomcool.lodinfra.Resource;
import com.github.artyomcool.lodinfra.Utils;
import com.github.artyomcool.lodinfra.h3common.Archive;
import com.github.artyomcool.lodinfra.h3common.DefInfo;
import com.github.artyomcool.lodinfra.h3common.LodFile;
import com.github.artyomcool.lodinfra.h3common.Png;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTreeTableView;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.application.Application;
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
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.control.SegmentedButton;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
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

import static com.github.artyomcool.lodinfra.ui.Ui.*;
import static javafx.scene.control.TreeTableView.UNCONSTRAINED_RESIZE_POLICY;

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

    private Consumer<TreeItem<Item>> onFilesChangedAction = t -> {
    };
    private Consumer<String> onFilterChanged = s -> {
    };

    private Stage primaryStage;
    private ResourceCompareView preview;
    private TreeItem<Item> rootItem;
    private WatchService watchService;
    private TextField search;
    private CheckBox searchRegex;
    private CheckBox searchCase;
    private Pattern searchPattern;
    private Scene scene;

    public DiffUi(Path localPath, Path remotePath, Properties cfg, Path logs, String nick) {
        this.localPath = localPath.toAbsolutePath();
        this.remotePath = remotePath.toAbsolutePath();
        this.logs = logs;
        this.nick = nick;
        this.cfg = cfg;
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        watchService.close();
        preview.stop();
    }

    record FileInfo(Path path, String name, FileTime lastModified, Long size, boolean isDirectory, boolean isFile,
                    boolean isLocal) {

        private static final SimpleDateFormat date = new SimpleDateFormat("yy-MM-dd");
        private static final SimpleDateFormat today = new SimpleDateFormat("HH:mm");

        public FileInfo foldInto(FileInfo local) {
            return new FileInfo(
                    path,
                    local.path.getParent().relativize(path).toString(),
                    lastModified,
                    size,
                    isDirectory,
                    isFile,
                    isLocal
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

    record LastTs(String relativePath, long ts, boolean local) {

        public String toLine() {
            return relativePath + ";" + ts + ";" + local;
        }

        public static LastTs fromLine(String line) {
            String[] args = line.split(";");
            return new LastTs(
                    args[0],
                    Long.parseLong(args[1]),
                    Boolean.parseBoolean(args[2])
            );
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

            status = status(null, null);
        }

        Item(FileInfo local, FileInfo remote, LastTs removedLocal, LastTs removedRemote) {
            this.local = local;
            this.remote = remote;
            this.isSynthetic = false;

            status = status(removedLocal, removedRemote);
        }

        public Item foldInto(Item value) {
            return new Item(local.foldInto(value.local), remote.foldInto(value.remote));
        }

        private ItemStatus status(LastTs removedLocal, LastTs removedRemote) {
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
            if (compare == 0) {
                return ItemStatus.SAME;
            }

            if (removedLocal != null) {
                if (compare(FileTime.fromMillis(removedLocal.ts), remote.lastModified) != 0) {
                    return ItemStatus.CONFLICT;
                }
                return ItemStatus.LOCAL_NEWER;
            }

            if (removedRemote != null) {
                if (compare(local.lastModified, FileTime.fromMillis(removedRemote.ts)) != 0) {
                    return ItemStatus.CONFLICT;
                }
                return ItemStatus.REMOTE_NEWER;
            }

            return compare > 0 ? ItemStatus.LOCAL_NEWER : ItemStatus.REMOTE_NEWER;
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
            preview = new ResourceCompareView(cfg, localPath.resolve("restore"));
            preview.setPadding(new Insets(4, 4, 2, 4));
            preview.start();
            ScrollPane pane = new ScrollPane(preview);
            pane.setPrefWidth(480);
            pane.setFitToWidth(true);
            SplitPane box = new SplitPane(lastFiles, pane);
            box.setDividerPosition(0, 0);
            root.getChildren().add(box);

            scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());

            primaryStage.setWidth(1200);
            primaryStage.setHeight(800);
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

    private void export(TreeItem<Item> item) throws IOException {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Path for export");
        File file = directoryChooser.showDialog(scene.getWindow());
        if (file != null) {
            export(item, file.toPath());
        }
    }

    private void export(TreeItem<Item> item, Path output) throws IOException {
        if (item.isLeaf()) {
            if (item.getValue().local.isFile) {
                Path lod = Resource.pathOfLod(item.getValue().local.path);
                if (lod != null) {
                    if (lod.getFileName().toString().toLowerCase().endsWith(".snd")) {
                        exportSnd(item.getValue().local.path, output);
                        return;
                    }
                }
                DefInfo defInfo = DefInfo.load(item.getValue().local.path);
                if (defInfo != null) {
                    List<DefInfo.Frame> frames = defInfo.frames();
                    for (DefInfo.Frame frame : frames) {
                        String str = Resource.fileNamePossibleInLod(defInfo.path);
                        int ext = str.indexOf('.');
                        if (ext != -1) {
                            str = str.substring(0, ext);
                        }
                        Path path = output.resolve(str + "_" + frame.name + ".png");
                        ByteBuffer pack = Png.pack(frame, true, true);
                        if (pack == null) {
                            return;
                        }
                        byte[] tmp = new byte[pack.remaining()];
                        pack.get(tmp);
                        Files.write(path, tmp);
                    }
                }
            }
        } else {
            for (TreeItem<Item> child : item.getChildren()) {
                export(child, output);
            }
        }
    }

    private void exportSnd(Path path, Path output) {
        ImgFilesUtils.processFile(path, null, buffer -> {
            try (FileOutputStream stream = new FileOutputStream(output.resolve(Resource.fileNamePossibleInLod(path) + ".wav").toFile())) {
                try (FileChannel fc = stream.getChannel()) {
                    fc.write(buffer);
                }
            }
            return null;
        });
    }

    private VBox files(StackPane root) throws IOException {
        ToggleButton fetch = new ToggleButton("Fetch");
        fetch.setGraphic(Icons.downloadIcon());
        ToggleButton observe = new ToggleButton("Observe");
        observe.setGraphic(Icons.observe());
        ToggleButton inProgress = new ToggleButton("In Progress");
        inProgress.setGraphic(Icons.inProgress());
        ToggleButton push = new ToggleButton("Push");
        push.setGraphic(Icons.uploadIcon());
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

        HBox rightPanel = new HBox();
        HBox modesWrapper = new HBox(modes);
        rightPanel.setAlignment(Pos.CENTER_RIGHT);
        modesWrapper.setAlignment(Pos.CENTER);
        rightPanel.setSpacing(2);
        rightPanel.setPadding(padding);
        rightPanel.setMinWidth(140);
        modesWrapper.setPadding(padding);
        HBox panelWrapper = new HBox(modesWrapper, rightPanel);
        HBox.setHgrow(modesWrapper, Priority.ALWAYS);
        panelWrapper.setAlignment(Pos.CENTER);

        StackPane listWrapper = new StackPane();
        Runnable r = () -> {
            searchPattern = null;
            onFilterChanged.accept(search.getText());
        };
        search = new TextField();
        search.setPromptText("Search...");
        search.textProperty().addListener((observable, oldValue, newValue) -> r.run());
        searchCase = new JFXCheckBox("Cc");
        searchRegex = new JFXCheckBox(".*");
        searchCase.setOnAction(e -> r.run());
        searchRegex.setOnAction(e -> r.run());
        VBox listParent = new VBox(panelWrapper, line(growH(search), searchCase, searchRegex), listWrapper);
        listParent.setFillWidth(true);
        VBox.setMargin(panelWrapper, padding);
        VBox.setVgrow(listWrapper, Priority.ALWAYS);

        fetch.selectedProperty().addListener(new ChangeListener<>() {

            private TreeItem<Item> cachedGlobalRoot;
            private TreeItem<Item> itemTreeItem;

            final Map<Item, ItemAction> fetchActions = new HashMap<>();
            final JFXTreeTableView<Item> fetchList = createListComponent(false, false, fetchActions);

            final Button fetchButton = new Button("Fetch", Icons.downloadIcon());

            {
                fetchButton.setOnAction(a -> {
                    for (Map.Entry<Item, ItemAction> entry : fetchActions.entrySet()) {
                        if (entry.getValue() != ItemAction.REMOTE_TO_LOCAL) {
                            continue;
                        }

                        Item item = entry.getKey();
                        if (item.isSynthetic || item.remote.isDirectory) {
                            continue;
                        }
                        if (item.remote.lastModified == null) {
                            try {
                                Files.delete(item.local.path);
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
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
                        for (Map.Entry<Item, ItemAction> entry : fetchActions.entrySet()) {
                            Item item = entry.getKey();
                            if (item.status == ItemStatus.CONFLICT) {
                                entry.setValue(ItemAction.NOTHING);
                            }
                        }
                    }

                    rightPanel.getChildren().setAll(fetchButton);
                    listWrapper.getChildren().setAll(fetchList);

                    onFilterChanged = s -> updateRoot();
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

            final Button pushButton = new Button("Push", Icons.uploadIcon());

            {
                pushButton.setOnAction(a -> {
                    StringBuilder files = new StringBuilder();
                    List<Map.Entry<Item, ItemAction>> actions = new ArrayList<>(pushActions.entrySet());
                    actions.sort(Comparator.comparing(c -> c.getKey().local.name.toLowerCase()));
                    for (Map.Entry<Item, ItemAction> entry : actions) {
                        if (entry.getValue() != ItemAction.LOCAL_TO_REMOTE) {
                            continue;
                        }

                        Item item = entry.getKey();
                        if (item.isSynthetic || !item.local.isFile) {
                            continue;
                        }

                        files.append(item.local.name).append("\r\n");
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
                        if (item.isSynthetic || !item.local.isFile) {
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

            final Button revertButton = new Button("Revert (!)", Icons.downloadIcon(Color.DARKRED));

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
                    onFilterChanged = s -> updateRoot();
                    if (cachedGlobalRoot != rootItem) {
                        updateRoot();
                    }
                    rightPanel.getChildren().setAll(revertButton, pushButton);
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
            private TreeItem<Item> gameRoot;
            final Map<Item, ItemAction> observeActions = new HashMap<>();
            final JFXTreeTableView<Item> observeList = createListComponent(true, false, observeActions);

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    if (cachedGlobalRoot != rootItem) {
                        updateRoot();
                    }
                    rightPanel.getChildren().clear();
                    listWrapper.getChildren().setAll(observeList);

                    onFilterChanged = s -> updateRoot();
                    onFilesChangedAction = treeItem -> {
                        rootItem = treeItem;
                        updateRoot();
                    };
                }
            }

            private void updateRoot() {
                if (gameRoot == null) {
                    gameRoot = loadTree(Path.of(cfg.getProperty("gameDir"), "Data"));
                }
                FileInfo root = new FileInfo(null, "Root", null, null, true, false, true);
                TreeItem<Item> rootItem = new TreeItem<>(new Item(root, root));
                rootItem.getChildren().add(DiffUi.this.filterForObserve(DiffUi.this.rootItem));
                rootItem.getChildren().add(DiffUi.this.filterForObserve(gameRoot));
                for (TreeItem<Item> child : rootItem.getChildren()) {
                    child.setExpanded(true);
                }
                itemTreeItem = rootItem;

                cachedGlobalRoot = DiffUi.this.rootItem;
                observeList.setRoot(itemTreeItem);
            }
        });
        inProgress.selectedProperty().addListener(new ChangeListener<>() {

            private TreeItem<Item> cachedGlobalRoot;
            private TreeItem<Item> itemTreeItem;
            final Map<Item, ItemAction> inProgress = new HashMap<>();
            final JFXTreeTableView<Item> inProgressList = createListComponent(true, false, inProgress);

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    if (cachedGlobalRoot != rootItem) {
                        updateRoot();
                    }
                    rightPanel.getChildren().clear();
                    listWrapper.getChildren().setAll(inProgressList);

                    onFilterChanged = s -> updateRoot();
                    onFilesChangedAction = treeItem -> {
                        rootItem = treeItem;
                        updateRoot();
                    };
                }
            }

            private void updateRoot() {
                cachedGlobalRoot = rootItem;
                itemTreeItem = DiffUi.this.filterForInProgress(localPath.resolve("restore"));

                inProgressList.setRoot(itemTreeItem);
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

        name.setMinWidth(260);

        list.getColumns().setAll(
                name,
                timeA,
                timeB,
                sizeA,
                sizeB
        );

        timeA.setPrefWidth(65);
        timeB.setPrefWidth(65);
        sizeA.setPrefWidth(2);
        sizeB.setPrefWidth(2);

        Font regular = Font.font("monospace", 11);
        Font bold = Font.font("monospace", FontWeight.BOLD, 11);

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
                        super.setGraphic(null);

                        if (empty) {
                            super.setText(null);
                            return;
                        }

                        super.setText(text);

                        TreeItem<Item> treeItem = getTableRow().getTreeItem();
                        if (treeItem == null) {
                            return;
                        }

                        Item item = treeItem.getValue();
                        ItemAction action = actions.getOrDefault(item, ItemAction.NOTHING);
                        if (item.status == ItemStatus.CONFLICT) {
                            setTextFill(Color.DARKMAGENTA);
                            setFont(bold);

                            if (!observe && !item.isSynthetic) {
                                super.setGraphic(checkBox);
                            }
                            checkBox.setSelected(action != ItemAction.NOTHING);
                            return;
                        }

                        if (observe) {
                            if (item.status == ItemStatus.REMOTE_NEWER) {
                                setTextFill(Color.DARKBLUE);
                                setFont(bold);
                            } else if (item.status == ItemStatus.LOCAL_NEWER) {
                                setTextFill(Color.DARKGOLDENROD);
                                setFont(bold);
                            } else {
                                setTextFill(Color.BLACK);
                                setFont(regular);
                            }
                            return;
                        }

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
        sizeA.setCellFactory(new Callback<>() {
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
            final ContextMenu contextMenu = new ContextMenu();
            final MenuItem export = menuItem("Export", () -> export(getTreeItem()));

            {
                setContextMenu(contextMenu);
            }

            {
                MenuItem validate = new MenuItem("Validate");
                Pattern skipFrames = Pattern.compile("0w_.*");
                validate.setOnAction(event -> applyToLeafs(getTreeItem() == null ? rootItem : getTreeItem(), item -> {
                    String name = item.local.path.getFileName().toString().toLowerCase();
                    if (name.endsWith(".def")) {
                        //ImgFilesUtils.validateDefNames(item.local.path, skipFrames);
                        //ImgFilesUtils.validateDefColors(item.local.path);
                        //ImgFilesUtils.validateDefSpecColors(item.local.path);
                    } else if (name.endsWith(".d32")) {
                        //ImgFilesUtils.validateD32Colors(item.local.path);
                    } else if (name.endsWith(".p32")) {
                        //ImgFilesUtils.validateP32Colors(item.local.path);
                    }
                }));
                MenuItem fixDefNaming = new MenuItem("Fix def naming");
                fixDefNaming.setOnAction(event -> applyToLeafs(getTreeItem(), item -> {
                    String name = item.local.path.getFileName().toString().toLowerCase();
                    if (name.endsWith(".def")) {
                        //ImgFilesUtils.fixDefNames(item.local.path, skipFrames);
                    }
                }));
                MenuItem fixColors = new MenuItem("Fix d32/p32 alpha colors");
                fixColors.setOnAction(event -> applyToLeafs(getTreeItem() == null ? rootItem : getTreeItem(), item -> {
                    String name = item.local.path.getFileName().toString().toLowerCase();
                    if (name.endsWith(".d32")) {
                        //ImgFilesUtils.fixD32Colors(item.local.path);
                    } else if (name.endsWith(".p32")) {
                        //ImgFilesUtils.fixP32Colors(item.local.path);
                    }
                }));

                //setContextMenu(new ContextMenu(validate, fixDefNaming, fixColors));
            }

            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    contextMenu.getItems().clear();
                    setOnMouseClicked(e -> {
                        if (e.getClickCount() == 2) {
                            if (e.isControlDown()) {
                                try {
                                    Runtime.getRuntime().exec("explorer /select, " + item.local);
                                } catch (IOException ex) {
                                    throw new RuntimeException(ex);
                                }
                                e.consume();
                            } else {
                                if (item.local.isFile && item.local.name.endsWith(".history")) {
                                    DefEditor root = new DefEditor(localPath.resolve("restore"));
                                    try {
                                        root.loadHistory(item.local.path);
                                    } catch (IOException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                    root.showModal(getScene());
                                }
                            }
                        }
                    });
                    if (item.remote.lastModified != null) {
                        contextMenu.getItems().add(export);
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
        list.setColumnResizePolicy(UNCONSTRAINED_RESIZE_POLICY);
        return list;
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
            Utils.deleteDir(path);
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

    /*private void unpackD32(Item item) {
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
            D32 d32 = new D32(buffer);
            String[] groupNames = ADef.groupNames(d32.groups().size());

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
                String[] groupNames = ADef.groupNames(def.groups.size());

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
    }*/

    private void expandLod(TreeItem<Item> treeItem) {
        TreeSet<String> allResources = new TreeSet<>();
        Map<String, Archive.Element> localMapping = new HashMap<>();
        FileInfo local = treeItem.getValue().local;

        try {
            if (local.isFile) {
                Archive lod = LodFile.load(local.path);
                for (Archive.Element subFile : lod.files()) {
                    String name = subFile.name().toLowerCase();
                    allResources.add(name);
                    localMapping.put(name, subFile);
                }
            }

            List<TreeItem<Item>> items = new ArrayList<>();
            for (String res : allResources) {
                Archive.Element localMeta = localMapping.get(res);
                String localName = localMeta.name();

                FileInfo localFile = new FileInfo(
                        Resource.pathInLod(local.path, localName),
                        localName,
                        local.lastModified,
                        (long) localMeta.uncompressedSize(),
                        false,
                        true,
                        true
                );

                items.add(new TreeItem<>(new Item(localFile, localFile, true)));
            }
            treeItem.getChildren().setAll(items);
            treeItem.setExpanded(true);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private TreeItem<Item> filterForObserve(TreeItem<Item> rootItem) {
        Predicate<TreeItem<Item>> preFilter = item -> true;
        Function<TreeItem<Item>, TreeItem<Item>> fold = item -> {
            if (item.getChildren().size() == 1) {
                TreeItem<Item> treeItem = item.getChildren().remove(0);
                treeItem.setValue(treeItem.getValue().foldInto(item.getValue()));
                return treeItem;
            } else if (item.getChildren().isEmpty()) {
                if (!item.getValue().local.isFile && !item.getValue().remote.isFile) {
                    return null;
                }
                if (!matchesToFilter(item.getValue().local.name)) {
                    return null;
                }
            }
            if (!search.getText().isEmpty()) {
                item.setExpanded(true);
            }
            return item;
        };
        TreeItem<Item> filter = filter(rootItem, preFilter, fold);
        return filter == null ? new TreeItem<>(rootItem.getValue()) : filter;
    }

    private TreeItem<Item> filterForInProgress(Path restore) {
        FileInfo rootFile = new FileInfo(restore, "Root", null, null, true, false, true);
        Item rootItem = new Item(rootFile, rootFile, true);
        TreeItem<Item> root = new TreeItem<>(rootItem);
        try (Stream<Path> sessions = Files.list(restore)) {
            sessions.forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        FileInfo sessionFile = new FileInfo(path, path.getFileName().toString(), Files.getLastModifiedTime(path), null, true, false, true);
                        TreeItem<Item> sessionTree = new TreeItem<>(new Item(sessionFile, sessionFile, false));
                        root.getChildren().add(sessionTree);
                        Set<Path> files = new HashSet<>();
                        try (Stream<Path> edits = Files.list(path)) {
                            edits.forEach(edit -> {
                                try {
                                    String name = edit.getFileName().toString();
                                    String lowName = name.toLowerCase();
                                    if (lowName.endsWith(".tmp")) {
                                        Files.delete(edit);
                                        return;
                                    }
                                    if (lowName.endsWith(".backup")) {
                                        Path history = edit.resolveSibling(name.substring(0, name.length() - ".backup".length()));
                                        if (Files.exists(history)) {
                                            Files.delete(edit);
                                            return;
                                        }
                                        Files.move(edit, history);
                                        edit = history;
                                    }
                                    files.add(edit);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        }
                        List<Path> sortedFiles = new ArrayList<>(files);
                        sortedFiles.sort(Comparator.<Path>naturalOrder().reversed());
                        for (Path edit : sortedFiles) {
                            FileInfo editFile = new FileInfo(edit, edit.getFileName().toString(), Files.getLastModifiedTime(edit), Files.size(edit), false, true, true);
                            sessionTree.getChildren().add(new TreeItem<>(new Item(editFile, editFile)));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Comparator<TreeItem<Item>> comparator = Comparator.comparing(s -> s.getValue().local.path);
        root.getChildren().sort(comparator.reversed());
        return root;
    }

    private TreeItem<Item> filterForFetch() {
        Predicate<TreeItem<Item>> preFilter = item -> {
            if (!item.getChildren().isEmpty()) {
                return !item.getValue().local.name.startsWith("[");
            }
            ItemStatus status = item.getValue().status;
            return status == ItemStatus.REMOTE_NEWER
                    || status == ItemStatus.REMOVED_REMOTE
                    || status == ItemStatus.CONFLICT;
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
            ItemStatus status = item.getValue().status;
            return status == ItemStatus.LOCAL_NEWER || status == ItemStatus.CONFLICT;
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

    private boolean matchesToFilter(String itemName) {
        boolean regex = searchRegex.isSelected();
        boolean caseSensitive = searchCase.isSelected();
        String searchText = search.getText();
        if (!regex) {
            if (!caseSensitive) {
                itemName = itemName.toLowerCase();
                searchText = searchText.toLowerCase();
            }
            if (!searchText.contains("*")) {
                return itemName.contains(searchText);
            }

            String[] parts = searchText.split("\\*");
            if (parts.length == 0) {
                return true;
            }
            if (!itemName.startsWith(parts[0])) {
                return false;
            }
            int startWith = 0;
            for (String part : parts) {
                int next = itemName.indexOf(part, startWith);
                if (next == -1) {
                    return false;
                }
                startWith = next + part.length();
            }
            return true;
        }

        if (searchPattern == null) {
            searchPattern = Pattern.compile(searchText, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        }

        return searchPattern.matcher(itemName).matches();
    }

    private TreeItem<Item> filter(
            TreeItem<Item> item,
            Predicate<TreeItem<Item>> preFilter,
            Function<TreeItem<Item>, TreeItem<Item>> fold
    ) {
        if (!preFilter.test(item)) {
            return null;
        }
        if ((item.getValue().local.isFile || item.getValue().remote.isFile) && item.isLeaf()
                && !matchesToFilter(item.getValue().local.name)) {
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
                new FileInfo(localPath, "Local files", Files.getLastModifiedTime(localPath), null, true, false, true),
                new FileInfo(remotePath, "Remote files", Files.getLastModifiedTime(remotePath), null, true, false, false)
        ));


        Path lastSyncTs = localPath.resolve("lastSyncTs");
        Map<Path, LastTs> maybeRemovedLocal = new HashMap<>();
        Map<Path, LastTs> maybeRemovedRemote = new HashMap<>();
        Set<LastTs> prevSync = new HashSet<>();
        try (var lines = Files.lines(lastSyncTs)) {
            lines.map(LastTs::fromLine).forEach(f -> {
                (f.local ? maybeRemovedLocal : maybeRemovedRemote).put(Path.of(f.relativePath), f);
                prevSync.add(f);
            });
        } catch (NoSuchFileException ignored) {
        }

        Map<Path, TreeItem<Item>> expandedTree = new HashMap<>();
        List<LastTs> nowExists = new ArrayList<>();

        for (Path path : allPaths) {
            if (path.getFileName().toString().isEmpty()) {
                expandedTree.put(path, rootItem);
                continue;
            }

            Path local = localPath.resolve(path);
            Path remote = remotePath.resolve(path);

            boolean localExists = Files.exists(local);
            boolean remoteExists = Files.exists(remote);

            LastTs removedLocal = localExists ? null : maybeRemovedLocal.get(path);
            LastTs removedRemote = remoteExists ? null : maybeRemovedRemote.get(path);

            boolean localIsDirectory = Files.isDirectory(local);
            boolean remoteIsDirectory = Files.isDirectory(remote);

            FileInfo localFile = new FileInfo(
                    local,
                    path.getFileName().toString(),
                    localExists && !localIsDirectory ? Files.getLastModifiedTime(local) : null,
                    localExists && !localIsDirectory ? Files.size(local) : null,
                    localIsDirectory,
                    localExists && !localIsDirectory,
                    true
            );
            FileInfo remoteFile = new FileInfo(
                    remote,
                    path.getFileName().toString(),
                    remoteExists && !remoteIsDirectory ? Files.getLastModifiedTime(remote) : null,
                    remoteExists && !remoteIsDirectory ? Files.size(remote) : null,
                    remoteIsDirectory,
                    remoteExists && !remoteIsDirectory,
                    false
            );

            if (localExists) {
                maybeRemovedLocal.remove(path);
                nowExists.add(new LastTs(path.toString(), localFile.lastModified == null ? 0 : localFile.lastModified.toMillis(), true));
            }

            if (remoteExists) {
                maybeRemovedRemote.remove(path);
                nowExists.add(new LastTs(path.toString(), remoteFile.lastModified == null ? 0 : remoteFile.lastModified.toMillis(), false));
            }

            TreeItem<Item> parent = path.getParent() == null ? rootItem : expandedTree.get(path.getParent());
            TreeItem<Item> item = new TreeItem<>(new Item(localFile, remoteFile, removedLocal, removedRemote));
            parent.getChildren().add(item);
            expandedTree.put(path, item);
        }

        Set<LastTs> nowSync = new HashSet<>();
        nowSync.addAll(maybeRemovedLocal.values());
        nowSync.addAll(maybeRemovedRemote.values());
        nowSync.addAll(nowExists);
        if (!prevSync.equals(nowSync)) {
            Files.write(lastSyncTs, nowSync.stream().map(LastTs::toLine).toList());
        }

        return rootItem;
    }

    private TreeItem<Item> loadTree(Path path) {
        try {
            String ignoreCommon = cfg.getProperty("ignore.common", "$^");
            Pattern ignoreLocal = Pattern.compile("(" + cfg.getProperty("ignore.local", "$^") + ")|(" + ignoreCommon + ")");

            FileInfo info = new FileInfo(path, "Game", Files.getLastModifiedTime(path), null, true, false, true);
            TreeItem<Item> rootItem = new TreeItem<>(new Item(info, info));
            List<Path> allPaths;
            Map<Path, TreeItem<Item>> expandedTree = new HashMap<>();

            try (Stream<Path> w1 = Files.walk(path)) {
                allPaths = w1.filter(p -> !ignoreLocal.matcher(p.toAbsolutePath().normalize().toString()).matches())
                        .map(path::relativize)
                        .toList();
            }

            for (Path p : allPaths) {
                if (p.getFileName().toString().isEmpty()) {
                    expandedTree.put(p, rootItem);
                    continue;
                }

                Path local = path.resolve(p);

                boolean localExists = Files.exists(local);
                boolean localIsDirectory = Files.isDirectory(local);
                FileInfo file = new FileInfo(
                        local,
                        p.getFileName().toString(),
                        localExists && !localIsDirectory ? Files.getLastModifiedTime(local) : null,
                        localExists && !localIsDirectory ? Files.size(local) : null,
                        localIsDirectory,
                        localExists && !localIsDirectory,
                        true
                );

                TreeItem<Item> parent = p.getParent() == null ? rootItem : expandedTree.get(p.getParent());
                TreeItem<Item> item = new TreeItem<>(new Item(file, file));

                if (localExists && !localIsDirectory) {
                    String itemName = p.getFileName().toString().toLowerCase();
                    if (itemName.endsWith(".lod") || itemName.endsWith(".snd")) {
                        expandLod(item);
                    }
                }

                parent.getChildren().add(item);
                expandedTree.put(p, item);
            }
            return rootItem;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

        Set<Path> allPaths = new TreeSet<>();
        allPaths.addAll(remotePaths);
        allPaths.addAll(localPaths);
        return allPaths;
    }

    private void show(TreeItem<Item> treeItem) {
        if (treeItem == null) {
            return;
        }
        preview.show(treeItem.getValue().local.path, treeItem.getValue().remote.path);

        DefCompareView[] node = new DefCompareView[1];

        MenuItem showIssues = new MenuItem("Show issues");
        /*showIssues.setOnAction(a -> {
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
        });*/

        MenuItem showFrame = new MenuItem("Show frame");
        showFrame.setOnAction(a -> {
            TextInputDialog dialog = new TextInputDialog("Frame number");
            Optional<String> result = dialog.showAndWait();
            //result.ifPresent(s -> node[0].showFrame(Integer.parseInt(s)));
        });
        ContextMenu menu = new ContextMenu(showIssues, showFrame);

        EventHandler<ContextMenuEvent> handler = e -> {
            node[0] = (DefCompareView) e.getSource();
            menu.show(node[0], e.getScreenX(), e.getScreenY());
        };
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

    enum ItemStatus {
        SAME,
        LOCAL_NEWER,
        REMOTE_NEWER,
        CONFLICT,
        REMOVED_LOCAL,
        REMOVED_REMOTE
    }

    enum ItemAction {
        NOTHING,
        LOCAL_TO_REMOTE,
        REMOTE_TO_LOCAL
    }

}