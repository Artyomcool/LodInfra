package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.Pack;
import com.github.artyomcool.lodinfra.Utils;
import com.github.artyomcool.lodinfra.h3common.*;
import com.jfoenix.controls.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.skin.SeparatorSkin;
import javafx.scene.control.skin.TreeViewSkin;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.artyomcool.lodinfra.ui.Ui.*;

public class DefEditor extends StackPane {

    private static final DataFormat JAVA_FORMAT = new DataFormat("application/x-java-serialized-object");
    private static final String DROP_HINT_STYLE = "-fx-border-color: #eea82f; -fx-border-width: 0 0 2 0; -fx-padding: 3 3 1 3";
    private static final Executor FILE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DefEditor file thread");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((thread1, throwable) -> throwable.printStackTrace());
        return thread;
    });
    private static final int NO_HIGHLIGHT = 0x00ffffff;
    private final Path restore;
    private final int time;
    private Path currentRestore;
    private int highlightedColor = NO_HIGHLIGHT;

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
        view.setPrefHeight(2000);
        view.setPrefWidth(220);
        view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        return view;
    }

    private boolean react = true;

    private final Label path = new Label();
    private final Label fullWidth = new Label();
    private final Label fullHeight = new Label();
    private final JFXComboBox<DefType> defType = new JFXComboBox<>();

    {
        defType.setItems(FXCollections.observableList(DefType.VALUES));
    }

    private final JFXTreeView<Object> groupsAndFrames = createGroupsAndFrames();
    private final DefView preview = new DefView();
    private final DefControl control = new DefControl(preview).noDiff();
    private final AnimationSpeedField animationSpeed = new AnimationSpeedField(this::nextFrame);
    private final JFXCheckBox loop = new JFXCheckBox("Loop");
    private final JFXCheckBox lockGroup = new JFXCheckBox("Lock group");

    private final List<TreeItem<Object>> draggedItem = new ArrayList<>();
    private final ListView<HistoryItem> history = new JFXListView<>();

    {
        history.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (react && newValue != null) {
                setDefInternal(newValue.def, newValue.def.first());
            }
        });
        history.setPadding(new Insets(0, 0, 0, 0));
        history.setOnMouseClicked(m -> {
            if (m.getClickCount() == 2) {
                HistoryItem selectedItem = history.getSelectionModel().getSelectedItem();
                if (selectedItem == null) {
                    return;
                }
                if (m.isControlDown()) {
                    int selectedIndex = history.getSelectionModel().getSelectedIndex();
                    DefInfo def = selectedItem.def;
                    setDefInternal(def, def.first());
                    update("Restore (" + selectedIndex + ")", true);
                } else {
                    DefInfo diffDef = DefInfo.makeDiff(
                            history.getItems().get(0).def,
                            selectedItem.def
                    );
                    setDefInternal(diffDef, diffDef.first());
                }
            }
        });
    }

    private final ImageView palette = new ImageView();

    {
        palette.setOnMouseClicked(m -> {
            if (m.isControlDown() && m.isShiftDown()) {
                removeHighlight();
                return;
            }
            if (m.getClickCount() == 2) {
                if (m.isControlDown()) {
                    if (highlightedColor != NO_HIGHLIGHT) {
                        replaceColor(highlightedColor, palette.getImage().getPixelReader().getArgb((int) m.getX(), (int) m.getY()));
                    }
                } else {
                    highlightColor(palette.getImage().getPixelReader().getArgb((int) m.getX(), (int) m.getY()));
                }
                return;
            }
        });
    }

    private TreeCell<Object> dropZone;

    {
        loop.setSelected(true);
        path.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        path.setMaxWidth(200);
        fullWidth.setPrefWidth(30);
        fullHeight.setPrefWidth(30);
    }

    private DefInfo currentDef() {
        return preview.getDef();
    }

    private void setDefToPreview(DefInfo def) {
        preview.setDef(def);
    }

    private final List<TreeItem<Object>> frames = new ArrayList<>();

    public DefEditor(Path restore) {
        this.restore = restore;
        this.time = LocalTime.now().toSecondOfDay();
        VBox top = new VBox(
                line(0, 0,
                        width(220, groupButtons(
                                jfxbutton("Extract", this::extract),
                                jfxbutton("Renew", this::renew),
                                jfxbutton("Save", this::save),
                                jfxbutton("Msk", this::saveMsk)
                        )),
                        new Separator(Orientation.VERTICAL),
                        line(new Label("Backgrounds"))
                ),
                new Separator() {
                    @Override
                    protected Skin<?> createDefaultSkin() {
                        return new SeparatorSkin(this) {
                            {
                                Region region = (Region) getChildrenUnmodifiable().get(0);
                                region.setPrefHeight(1);
                            }
                        };
                    }
                }
        );
        HBox left = line(0, 0,
                new VBox(
                        grow(groupsAndFrames),
                        groupButtons(
                                button("+Group", this::addGroup),
                                button("+Frames", this::insertFrames)
                        )
                ),
                new Separator(Orientation.VERTICAL)
        );
        VBox center = new VBox(
                grow(new ScrollPane(border(Color.GRAY, preview)) {
                    {
                        preview.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(() -> {
                            setHvalue(0.5);
                            setVvalue(0.5);
                        }));
                    }
                }),
                new Separator(),
                pad(2, growH(control)),
                new Separator(),
                line(loop, lockGroup, line(0, 0, new Label("Animation speed: "), animationSpeed, new Label(" ms"))),
                new Separator(),
                line(4, path, new Separator(Orientation.VERTICAL), fullWidth, fullHeight, new Separator(Orientation.VERTICAL), defType)
        );
        HBox right = line(4, 0,
                new Separator(Orientation.VERTICAL),
                growH(new VBox(8,
                        border(Color.LIGHTGRAY,
                                new VBox(4,
                                        height(220, width(220, new StackPane(palette))),
                                        groupButtons(
                                                button("Calculate", this::calculatePalette),
                                                button("Drop", this::dropPalette)
                                        )
                                )
                        ),
                        grow(border(Color.LIGHTGRAY, width(220, history)))
                ))
        );
        BorderPane root = new BorderPane(center, top, right, null, left);
        getChildren().setAll(root);

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
        preview.addOnChangedListener(() -> {
            if (!react) {
                return;
            }
            int frameIndex = preview.getGlobalIndex();
            if (frameIndex >= 0 && frameIndex < frames.size()) {
                groupsAndFrames.getSelectionModel().clearSelection();
                groupsAndFrames.getSelectionModel().select(frames.get(frameIndex));
                autoscroll();
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
                update("Deleted frames", false);
            }
        });
        groupsAndFrames.setContextMenu(new ContextMenu(delete));
    }

    public void setDef(DefInfo original, DefInfo.Frame frame) {
        DefInfo def = original.cloneBase();
        for (DefInfo.Group group : original.groups) {
            DefInfo.Group g = group.cloneBase(def);
            for (DefInfo.Frame fr : group.frames) {
                if (fr == frame) {
                    frame = fr.cloneBase(g);
                } else {
                    fr.cloneBase(g);
                }
            }
        }
        setDefInternal(def, frame);

        autoscroll();
        react = false;
        putHistory(def, "Initial load");
        react = true;
    }

    private void setDefInternal(DefInfo def, DefInfo.Frame frame) {
        setDefToPreview(def);
        path.setText(def.path == null ? "" : def.path.toString());
        fullWidth.setText(def.fullWidth + "");
        fullHeight.setText(def.fullHeight + "");
        defType.setValue(DefType.of(def.type));

        drawPalette(def);

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
    }

    private void drawPalette(DefInfo def) {
        if (def.palette == null) {
            palette.setImage(DefView.EMPTY);
            return;
        }
        int cw = 13;
        WritableImage img = new WritableImage(16 * cw, 16 * cw);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = def.palette[y * 16 + x];
                for (int i = 0; i < cw; i++) {
                    for (int j = 0; j < cw; j++) {
                        img.getPixelWriter().setArgb(x * cw + j, y * cw + i, color);
                    }
                }
            }
        }
        palette.setImage(img);
    }

    private void putHistory(DefInfo def, String action) {
        history.getItems().add(0, new HistoryItem(def, history.getItems().size() + ". " + action));
        history.getSelectionModel().select(0);
        saveHistory(new ArrayList<>(history.getItems()));
    }

    private void saveHistory(List<HistoryItem> items) {
        FILE_EXECUTOR.execute(() -> {
            try {
                if (currentRestore == null) {
                    Files.createDirectories(restore);
                    LocalDate now = LocalDate.now();

                    try (Stream<Path> pathStream = Files.list(restore)) {
                        pathStream.forEach(p -> {
                            LocalDate dateTime = LocalDate.parse(p.getFileName().toString());
                            if (dateTime.plusMonths(1).isBefore(now)) {
                                try {
                                    Utils.deleteDir(p);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    }
                    currentRestore = restore.resolve(now.toString());
                    Files.createDirectories(currentRestore);
                }

                String name = currentDef().path.getFileName().toString();
                if (name.contains("=@=@=")) {
                    name = name.substring(name.indexOf("=@=@=") + "=@=@=".length());
                }
                name = time + "-" + name;

                Path fileTmp = Files.createTempFile(currentRestore, "", ".history.tmp");
                try (SeekableByteChannel backup = Files.newByteChannel(fileTmp, StandardOpenOption.WRITE)) {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(10 * 1024 * 1024);

                    Set<String> hash = new HashSet<>();
                    for (HistoryItem item : items) {
                        for (DefInfo.Group group : item.def.groups) {
                            for (DefInfo.Frame frame : group.frames) {
                                if (hash.add(frame.pixelsSha)) {
                                    putString(buffer, frame.pixelsSha);
                                    backup.write(buffer.flip());
                                    buffer.clear();

                                    IntBuffer intBuf = buffer.asIntBuffer();
                                    intBuf.put(frame.pixels.asReadOnlyBuffer());
                                    buffer.position(buffer.position() + intBuf.position() * 4);
                                    backup.write(buffer.flip());
                                    buffer.clear();
                                }
                            }
                        }
                    }

                    putString(buffer, "");
                    buffer.putInt(items.size());
                    for (HistoryItem item : items) {
                        putString(buffer, item.action);
                        buffer.putInt(item.def.type);
                        buffer.putInt(item.def.fullWidth);
                        buffer.putInt(item.def.fullHeight);
                        buffer.putInt(item.def.palette == null ? 0 : 256);
                        if (item.def.palette != null) {
                            for (int color : item.def.palette) {
                                buffer.putInt(color);
                            }
                        }
                        buffer.putInt(item.def.groups.size());
                        for (DefInfo.Group group : item.def.groups) {
                            buffer.putInt(group.groupIndex);
                            putString(buffer, group.name);
                            buffer.putInt(group.frames.size());
                            for (DefInfo.Frame frame : group.frames) {
                                buffer.putInt(frame.fullWidth);
                                buffer.putInt(frame.fullHeight);
                                putString(buffer, frame.name);
                                buffer.putInt(frame.compression);
                                buffer.putInt(frame.frameDrawType);
                                putString(buffer, frame.pixelsSha);
                            }
                        }
                    }

                    backup.write(buffer.flip());
                }

                Path history = currentRestore.resolve(name + ".history");
                Path backup = currentRestore.resolve(name + ".history.backup");
                if (Files.exists(history)) {
                    Files.deleteIfExists(backup);
                    Files.move(history, backup, StandardCopyOption.ATOMIC_MOVE);
                }
                Files.move(fileTmp, history);
                Files.deleteIfExists(backup);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void putString(ByteBuffer buffer, String data) {
        byte[] bytes = data == null ? null : data.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes == null ? -1 : bytes.length);
        if (bytes != null) {
            buffer.put(bytes);
        }
    }

    private void autoscroll() {
        int selectedIndex = groupsAndFrames.getSelectionModel().getSelectedIndex();
        Skin<?> skin = groupsAndFrames.getSkin();
        if (skin == null) {
            return;
        }
        for (Object child : ((TreeViewSkin<?>) skin).getChildren()) {
            if (child instanceof VirtualFlow<?>) {
                IndexedCell<?> cell = ((VirtualFlow<?>) child).getFirstVisibleCell();
                if (cell == null) {
                    break;
                }
                int first = cell.getIndex();
                int last = ((VirtualFlow<?>) child).getLastVisibleCell().getIndex();
                if (selectedIndex <= first) {
                    groupsAndFrames.scrollTo(selectedIndex);
                } else if (selectedIndex >= last) {
                    groupsAndFrames.scrollTo(selectedIndex - (last - first) + 1);
                }
                break;
            }
        }
    }

    private void calculatePalette() {
        DefInfo def = currentDef();
        Set<Integer> colors = new HashSet<>();
        for (DefInfo.Group group : def.groups) {
            for (DefInfo.Frame frame : group.frames) {
                IntBuffer pixels = frame.pixels.duplicate();
                while (pixels.hasRemaining()) {
                    colors.add(pixels.get());
                }
            }
        }

        int specialColors = switch (DefInfo.compressionForType(def, defType.getValue().type)) {
            case 0 -> 0;
            case 1 -> 8;
            case 2, 3 -> 6;
            default -> throw showError("Unexpected value for: " + defType.getValue());
        };

        for (int i = 0; i < specialColors; i++) {
            int specColor = DefInfo.SPEC_COLORS[i];
            colors.remove(specColor);
        }


        int colorsCount = colors.size() + specialColors;
        if (colorsCount > 256) {
            System.err.println("Wrong colors count: " + colorsCount);
            ButtonType compact = new ButtonType("Compact", ButtonBar.ButtonData.APPLY);
            Alert alert = new Alert(Alert.AlertType.WARNING, "Palette to large: " + colorsCount, compact, ButtonType.CANCEL);
            if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.CANCEL) {
                return;
            }

            Map<Integer, Integer> colorReplacements = new HashMap<>();
            while (colors.size() + specialColors > 256) {
                ArrayList<Integer> cc = new ArrayList<>(colors);
                reducePaletteForOne(cc, colorReplacements);
                colors = new HashSet<>(cc);
            }

            List<Map.Entry<Integer, Integer>> replacements = new ArrayList<>(colorReplacements.entrySet());
            Map<Integer, Map.Entry<Integer, Integer>> replacementsMap = new HashMap<>();
            for (Map.Entry<Integer, Integer> replacement : replacements) {
                replacementsMap.put(replacement.getKey(), replacement);
            }

            for (Map.Entry<Integer, Integer> replacement : replacements) {
                Integer to = replacement.getValue();

                Map.Entry<Integer, Integer> rep = replacementsMap.get(to);
                while (rep != null) {
                    to = rep.getValue();
                    replacement.setValue(to);
                    rep = replacementsMap.get(to);
                }
            }

            DefInfo clone = def.cloneBase();
            Object value = groupsAndFrames.getSelectionModel().getSelectedItem().getValue();
            DefInfo.Frame selectedFrame = null;
            for (DefInfo.Group group : def.groups) {
                DefInfo.Group groupClone = group.cloneBase(clone);
                for (DefInfo.Frame frame : group.frames) {
                    IntBuffer from = frame.pixels.duplicate();
                    IntBuffer to = null;
                    while (from.hasRemaining()) {
                        int c = from.get();
                        Integer replace = colorReplacements.get(c);
                        if (replace != null) {
                            if (to == null) {
                                to = IntBuffer.allocate(frame.pixels.remaining());
                                to.put(frame.pixels.duplicate());
                                to.position(from.position() - 1);
                            }
                            to.put(replace);
                        } else {
                            if (to != null) {
                                to.put(c);
                            }
                        }
                    }

                    DefInfo.Frame cloneFrame = to == null
                            ? frame.cloneBase(groupClone)
                            : frame.cloneBase(groupClone, to.flip());
                    if (value == frame) {
                        selectedFrame = cloneFrame;
                    }
                }
            }

            clone.palette = toPalette(colors, specialColors);
            setDefInternal(clone, selectedFrame);
            drawPalette(clone);
            autoscroll();
            react = false;
            putHistory(clone, "Palette compaction");
            react = true;
            return;
        }
        int[] palette = toPalette(colors, specialColors);
        update("Palette", palette, Function.identity());
    }

    private void dropPalette() {
        update("Drop palette", null, Function.identity());
    }

    private static int[] toPalette(Set<Integer> colors, int specialColors) {
        int[] palette = new int[256];
        int i = 0;
        for (; i < specialColors; i++) {
            palette[i] = DefInfo.SPEC_COLORS[i];
        }
        TreeSet<Integer> tree = new TreeSet<>((c1, c2) -> {
            int r1 = c1 >>> 16 & 0xff;
            int r2 = c2 >>> 16 & 0xff;

            int g1 = c1 >>> 8 & 0xff;
            int g2 = c2 >>> 8 & 0xff;

            int b1 = c1 & 0xff;
            int b2 = c2 & 0xff;

            int y1 = r1 * 3 + b1 + g1 * 4;
            int y2 = r2 * 3 + b2 + g2 * 4;

            int compare = Integer.compare(y1, y2);
            return compare == 0 ? Integer.compare(c1, c2) : compare;
        });
        tree.addAll(colors);
        for (Integer c : tree) {
            palette[i++] = c;
        }
        while (i < 256) {
            palette[i++] = 0xFF000000;
        }
        return palette;
    }

    private void reducePaletteForOne(List<Integer> colors, Map<Integer, Integer> colorReplacements) {
        int diff = Integer.MAX_VALUE;
        int gc1 = 0;
        int gc2 = 0;

        for (int i = 0; i < colors.size(); i++) {
            int c1 = colors.get(i);
            for (int j = i + 1; j < colors.size(); j++) {
                int c2 = colors.get(j);
                int d = ImgFilesUtils.colorDifferenceForCompare(c1, c2);
                if (d < diff) {
                    gc1 = c1;
                    gc2 = c2;
                    diff = d;
                }
            }
        }

        int a = (((gc1 >>> 24) & 0xff) + ((gc2 >>> 24) & 0xff)) >>> 1;
        int r = (((gc1 >>> 16) & 0xff) + ((gc2 >>> 16) & 0xff)) >>> 1;
        int g = (((gc1 >>> 8) & 0xff) + ((gc2 >>> 8) & 0xff)) >>> 1;
        int b = ((gc1 & 0xff) + (gc2 & 0xff)) >>> 1;

        colors.remove((Integer) gc1);
        colors.remove((Integer) gc2);
        int gc = a << 24 | r << 16 | g << 8 | b;
        for (int specColor : DefInfo.SPEC_COLORS) {
            if (specColor == gc) {
                gc |= 0x0100;
                if (specColor == gc) {
                    gc &= ~0x0100;
                }
                break;
            }
        }
        colors.add(gc);
        if (gc1 != gc) {
            colorReplacements.put(gc1, gc);
        }
        if (gc2 != gc) {
            colorReplacements.put(gc2, gc);
        }
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

        update("Moved frames", true);

        event.setDropCompleted(success);
    }

    private void update(String action, boolean restoreSelection) {
        update(action, currentDef().palette, Function.identity());
    }

    private void update(String action, int[] palette, Function<IntBuffer, IntBuffer> frameProcessor) {
        DefInfo.Frame currentFrame = null;
        if (groupsAndFrames.getSelectionModel().getSelectedItem() != null) {
            Object value = groupsAndFrames.getSelectionModel().getSelectedItem().getValue();
            if (value instanceof DefInfo.Frame) {
                currentFrame = (DefInfo.Frame) value;
            }
        }
        DefInfo def = currentDef().cloneBase();
        def.palette = palette;
        for (TreeItem<Object> treeGroup : groupsAndFrames.getRoot().getChildren()) {
            DefInfo.Group group = ((DefInfo.Group) treeGroup.getValue()).cloneBase(def);
            treeGroup.setValue(group);
            for (TreeItem<Object> treeFrame : treeGroup.getChildren()) {
                DefInfo.Frame prevFrame = (DefInfo.Frame) treeFrame.getValue();
                DefInfo.Frame frame = prevFrame.cloneBase(group, frameProcessor.apply(prevFrame.pixels.duplicate()));
                if (currentFrame == prevFrame) {
                    currentFrame = frame;
                }
                treeFrame.setValue(frame);
                frames.add(treeFrame);
            }
        }
        react = false;
        setDefToPreview(def);
        if (currentFrame == null) {
            currentFrame = def.first();
        }
        preview.setFrame(currentFrame);
        drawPalette(def);
        putHistory(def, action);
        react = true;
    }

    private void clearDropLocation() {
        if (dropZone != null) {
            dropZone.setStyle("");
        }
    }

    private void highlightColor(int color) {
        System.out.println("Color is: 0x" + Integer.toHexString(color).toUpperCase());
        highlightedColor = color;
        preview.setTransformation(image -> {
            int multiply = 4;
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            WritableImage result = new WritableImage(width * multiply, height * multiply);
            int[] scanline = new int[(int) result.getWidth()];
            for (int y = 0, dy = 0; y < height; y++) {
                WritablePixelFormat<IntBuffer> format = PixelFormat.getIntArgbInstance();
                image.getPixelReader().getPixels(0, y, width, 1, format, scanline, 0, width);
                for (int src = width - 1, dst = scanline.length - 1; src >= 0; src--) {
                    boolean highlight = color == scanline[src];
                    for (int i = 0; i < multiply; i++, dst--) {
                        scanline[dst] = scanline[src];
                    }
                    if (highlight) {
                        scanline[dst + multiply] = 0xffffff00;
                        scanline[dst + 1] = 0xffffffff;
                    }
                }
                dy++;
                for (int i = 0; i < multiply - 2; i++, dy++) {
                    result.getPixelWriter().setPixels(0, dy, scanline.length, 1, format, scanline, 0, scanline.length);
                }
                for (int i = 0; i < scanline.length; i++) {
                    boolean highlight = color == scanline[i];
                    if (highlight) {
                        scanline[i] = 0xffff0000;
                    }
                }
                result.getPixelWriter().setPixels(0, dy++, scanline.length, 1, format, scanline, 0, scanline.length);
                result.getPixelWriter().setPixels(0, dy - multiply, scanline.length, 1, format, scanline, 0, scanline.length);
            }
            return result;
        });
    }

    private void removeHighlight() {
        highlightedColor = NO_HIGHLIGHT;
        preview.setTransformation(Function.identity());
    }

    private void replaceColor(int from, int to) {
        update(Integer.toHexString(from).toUpperCase() + "->" + Integer.toHexString(to).toUpperCase(),
                currentDef().palette,
                pixels -> {
                    IntBuffer result = IntBuffer.allocate(pixels.remaining());
                    while (pixels.hasRemaining()) {
                        int c = pixels.get();
                        if (c == from) {
                            c = to;
                        }
                        result.put(c);
                    }
                    return result.flip();
                }
        );
    }

    private void extract() {
        // TODO enforce no palette or valid palette
        DefInfo current = history.getItems().get(0).def;
        Path extracted = currentRestore.resolve(time + "_Extracted");
        try {
            Files.createDirectories(extracted);
            for (DefInfo.Group group : current.groups) {
                for (DefInfo.Frame frame : group.frames) {
                    ByteBuffer pack = Png.pack(frame);
                    String name = frame.name;
                    byte[] data = new byte[pack.remaining()];
                    pack.get(data);
                    Files.write(extracted.resolve(name + ".png"), data);
                }
            }

            Pack.APP.getHostServices().showDocument(extracted.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void renew() {
        DefInfo def = history.getItems().get(0).def;
        DefInfo current = def.cloneBase();
        Path extracted = currentRestore.resolve(time + "_Extracted");
        try {
            for (DefInfo.Group group : def.groups) {
                DefInfo.Group base = group.cloneBase(current);
                for (DefInfo.Frame frame : group.frames) {
                    String name = frame.name;
                    Path png = extracted.resolve(name + ".png");
                    Path bmp = extracted.resolve(name + ".bmp");
                    if (Files.exists(png)) {
                        DefInfo info = Png.load(ByteBuffer.wrap(Files.readAllBytes(png)));
                        frame.cloneBase(base, info.first().pixels);
                    } else if (Files.exists(bmp)) {
                        DefInfo info = Bmp.load(ByteBuffer.wrap(Files.readAllBytes(bmp)).order(ByteOrder.LITTLE_ENDIAN));
                        frame.cloneBase(base, info.fullWidth, info.fullHeight, info.first().pixels);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int w = -1;
        int h = -1;
        for (DefInfo.Group group : current.groups) {
            for (DefInfo.Frame frame : group.frames) {
                if (w == -1) {
                    w = frame.fullWidth;
                } else if (w != frame.fullWidth) {
                    throw showError("Width inconsistent: " + frame.name + " has width " + w);
                }
                if (h == -1) {
                    h = frame.fullHeight;
                } else if (w != frame.fullHeight) {
                    throw showError("Height inconsistent: " + frame.fullHeight + " has height " + h);
                }
            }
        }
        current.fullWidth = w;
        current.fullHeight = h;
        setDefInternal(current, current.first());
        drawPalette(current);
        autoscroll();
        react = false;
        putHistory(current, "Reloaded frames");
        react = true;
    }

    private void addGroup() {
        DefInfo.Group g = new DefInfo.Group(currentDef());
        g.groupIndex = g.def.groups.isEmpty() ? 0 : (g.def.groups.get(g.def.groups.size() - 1).groupIndex + 1);
        TreeItem<Object> item = new TreeItem<>(g);
        groupsAndFrames.getRoot().getChildren().add(item);
        update("New group", true);
    }

    private void insertFrames() {
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
        update("New frames", true);
    }

    private void save() {
        DefType type = defType.getSelectionModel().getSelectedItem();
        DefInfo def = history.getItems().get(0).def.withType(type.type);

        ByteBuffer packed = switch (type) {
            case DefDefault,
                    DefCombatCreature,
                    DefAdventureObject,
                    DefAdventureHero,
                    DefGroundTile,
                    DefMousePointer,
                    DefInterface,
                    DefCombatHero -> Def.pack(ensureWithPalette(def), getLinks(def));
            case D32 -> D32.pack(ensureNoPalette(def), getLinks(def));
            case P32 -> P32.pack(ensureOnlyFrame(ensureNoPalette(def)));
            case Pcx8 -> Pcx.pack(ensureOnlyFrame(ensureWithPalette(def)));
            case Pcx24 -> Pcx.pack(ensureNoAlpha(ensureOnlyFrame(ensureNoPalette(def))));
            case Unknown -> throw new UnsupportedOperationException("Unknown type");
        };

        DefInfo load = DefInfo.load(packed);
        load.path = def.path;
        setDef(load, load.first());

        try {
            Files.deleteIfExists(def.path);
            Files.write(def.path, packed.array());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveMsk() {
        DefInfo def = currentDef();
        if (def.fullWidth % 32 != 0 || def.fullHeight % 32 != 0) {
            throw new RuntimeException("Wrong width/height");
        }
        Msk msk = new Msk(def.fullWidth / 32, def.fullHeight / 32);
        for (DefInfo.Group group : def.groups) {
            for (DefInfo.Frame frame : group.frames) {
                for (int y = 0; y < frame.fullHeight; y++) {
                    a: for (int x = 0; x < frame.fullWidth; x++) {
                        int cm = frame.colorMul(x, y);
                        if (cm == DefInfo.SPEC_COLORS[0]) {
                            continue;
                        }
                        for (int i = 1; i < 6; i++) {
                            int specColor = DefInfo.SPEC_COLORS[i];
                            if (cm == specColor) {
                                msk.markIsShadow(x / 32, y / 32);
                                continue a;
                            }
                        }

                        msk.markIsDirty(x / 32, y / 32);
                    }
                }
            }
        }

        String name = def.path.getFileName().toString();
        Path path = def.path.resolveSibling(name.substring(0, name.indexOf(".")) + ".msk");
        try {
            Files.deleteIfExists(path);
            Files.write(path, msk.bytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static DefInfo.Frame ensureNoAlpha(DefInfo.Frame frame) {
        IntBuffer pixels = frame.pixels.duplicate();
        while (pixels.hasRemaining()) {
            int color = pixels.get();
            if (color >>> 24 != 0xff) {
                throw showError("There are pixels with alpha: 0x" + Integer.toHexString(color));
            }
        }
        return frame;
    }

    private static DefInfo.Frame ensureOnlyFrame(DefInfo def) {
        DefInfo.Frame frame = null;
        for (DefInfo.Group group : def.groups) {
            for (DefInfo.Frame f : group.frames) {
                if (frame == null) {
                    frame = f;
                } else {
                    throw showError("There should be only one frame");
                }
            }
        }
        throw showError("There should be exactly one frame");
    }

    private static DefInfo ensureNoPalette(DefInfo def) {
        if (def.palette != null) {
            throw showError("There should be no palette");
        }
        return def;
    }

    private static DefInfo ensureWithPalette(DefInfo def) {
        if (def.palette == null) {
            throw showError("There should be a palette");
        }
        Set<String> hashes = new HashSet<>();
        Set<Integer> palette = Arrays.stream(def.palette).boxed().collect(Collectors.toSet());
        for (DefInfo.Group group : def.groups) {
            for (DefInfo.Frame frame : group.frames) {
                if (hashes.add(frame.pixelsSha)) {
                    IntBuffer pixels = frame.pixels.duplicate();
                    while (pixels.hasRemaining()) {
                        int color = pixels.get();
                        if (!palette.contains(color)) {
                            throw showError("Palette doesn't contain color 0x" + Integer.toHexString(color));
                        }
                    }
                }
            }
        }
        return def;
    }

    private static Map<DefInfo.Frame, DefInfo.FrameInfo> getLinks(DefInfo def) {
        Map<String, Box> shaToBox = new HashMap<>();
        Map<DefInfo.PackedFrame, DefInfo.FrameInfo> frameInfoMap = new LinkedHashMap<>();
        for (DefInfo.Group group : def.groups) {
            for (DefInfo.Frame frame : group.frames) {
                Box box = shaToBox.computeIfAbsent(
                        frame.pixelsSha,
                        s -> DefInfo.calculateTransparent(frame.fullWidth, frame.fullHeight, frame.pixels)
                );
                DefInfo.FrameInfo info = frameInfoMap.computeIfAbsent(
                        new DefInfo.PackedFrame(frame, box),
                        DefInfo.FrameInfo::new
                );
                info.frames.add(frame);
            }
        }

        int sharedFramesCount = 0;
        Map<DefInfo.Frame, DefInfo.FrameInfo> result = new HashMap<>();
        for (DefInfo.FrameInfo value : frameInfoMap.values()) {
            String defName = def.path.getFileName().toString();
            String prefix = defName.substring(0, Math.min(8, defName.length()));
            if (value.frames.size() > 1) {
                value.name = String.format("%s_%03d", prefix, sharedFramesCount++);
            } else {
                DefInfo.Frame frame = value.frames.get(0);
                char group = (char) ('A' + frame.group.groupIndex);
                value.name = String.format("%s%s%03d", prefix, group + "", frame.group.frames.indexOf(frame));
            }
            for (DefInfo.Frame frame : value.frames) {
                result.put(frame, value);
            }
        }

        return result;
    }

    private static class HistoryItem {
        public final DefInfo def;
        public final String action;

        private HistoryItem(DefInfo def, String action) {
            this.def = def;
            this.action = action;
        }

        @Override
        public String toString() {
            return action;
        }
    }

}
