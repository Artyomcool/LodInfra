package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.h3common.Def;
import com.github.artyomcool.lodinfra.h3common.DefInfo;
import com.jfoenix.controls.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
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
import java.nio.IntBuffer;
import java.util.*;
import java.util.function.Function;

public class DefEditor extends StackPane {

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
        view.setPrefWidth(150);
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

    private final Button addGroup = new Button("+Group");
    private final Button insertFrames = new Button("+Frames");
    private final List<TreeItem<Object>> draggedItem = new ArrayList<>();
    private final ListView<HistoryItem> history = new JFXListView<>();

    {
        history.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (react && newValue != null) {
                react = false;
                setDefInternal(newValue.def, null);
                react = true;
            }
        });
    }

    private final ImageView palette = new ImageView();

    {
        palette.setOnMouseClicked(m -> {
            if (m.isControlDown()) {
                removeHighlight();
                return;
            }
            highlightColor(palette.getImage().getPixelReader().getArgb((int) m.getX(), (int) m.getY()));
        });
    }

    private final Button calculatePalette = new Button("Calculate");

    {
        calculatePalette.setOnAction(e -> {
            calculatePalette();
        });
    }

    private TreeCell<Object> dropZone;

    {
        loop.setSelected(true);
        path.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        path.setMaxWidth(200);
        fullWidth.setPrefWidth(30);
        fullHeight.setPrefWidth(30);

        addGroup.setOnAction(e -> {
            DefInfo.Group g = new DefInfo.Group(preview.getDef());
            g.groupIndex = g.def.groups.isEmpty() ? 0 : (g.def.groups.get(g.def.groups.size() - 1).groupIndex + 1);
            TreeItem<Object> item = new TreeItem<>(g);
            groupsAndFrames.getRoot().getChildren().add(item);
            update("New group", true);
        });
    }

    private final List<TreeItem<Object>> frames = new ArrayList<>();

    public DefEditor() {
        VBox top = new VBox(
                line(0, 0,
                        width(150, line(new Label("Buttons"))),
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
        HBox left = line(0, 0, new VBox(grow(groupsAndFrames), groupButtons(addGroup, insertFrames)), new Separator(Orientation.VERTICAL));
        VBox center = new VBox(
                grow(new ScrollPane(border(Color.GRAY, preview))),
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
                                        height(150, width(150, new StackPane(palette))),
                                        groupButtons(calculatePalette)
                                )
                        ),
                        grow(border(Color.LIGHTGRAY, width(150, history)))
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
            update("New frames", true);
        });
    }

    public void setDef(DefInfo original, DefInfo.Frame frame) {
        DefInfo def = original.cloneBase();
        for (DefInfo.Group group : original.groups) {
            DefInfo.Group g = group.cloneBase(def);
            def.groups.add(g);
            for (DefInfo.Frame fr : group.frames) {
                DefInfo.Frame f = fr.cloneBase(g);
                g.frames.add(f);
            }
        }
        setDefInternal(def, frame);

        autoscroll();
        react = false;
        putHistory(def, "Initial load");
        react = true;
    }

    private void setDefInternal(DefInfo def, DefInfo.Frame frame) {
        preview.setDef(def);
        path.setText(def.path == null ? "" : def.path.toString());
        fullWidth.setText(def.fullWidth + "");
        fullHeight.setText(def.fullHeight + "");
        defType.setValue(DefType.of(def.type));

        if (def.palette != null) {
            drawPalette(def);
        }

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
        int cw = 8;
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
        history.getItems().add(0, new HistoryItem(def, action));
        history.getSelectionModel().select(0);
    }

    private void autoscroll() {
        int selectedIndex = groupsAndFrames.getSelectionModel().getSelectedIndex();
        Skin<?> skin = groupsAndFrames.getSkin();
        if (skin == null) {
            return;
        }
        for (Object child : ((TreeViewSkin<?>) skin).getChildren()) {
            if (child instanceof VirtualFlow<?>) {
                int first = ((VirtualFlow<?>) child).getFirstVisibleCell().getIndex();
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
        DefInfo def = preview.getDef();
        Set<Integer> colors = new HashSet<>();
        for (DefInfo.Group group : def.groups) {
            for (DefInfo.Frame frame : group.frames) {
                IntBuffer pixels = frame.pixels.duplicate();
                while (pixels.hasRemaining()) {
                    colors.add(pixels.get());
                }
            }
        }
        boolean battleColors = false;
        int[] specColors = DefInfo.SPEC_COLORS;
        for (int i = 0; i < specColors.length; i++) {
            int specColor = specColors[i];
            if (colors.remove(specColor)) {
                if (i >= 6) {
                    battleColors = true;
                }
            }
        }
        int colorsCount = colors.size() + (battleColors ? 8 : 6);
        if (colorsCount <= 256) {
            int[] palette = new int[256];
            int i = 0;
            for (; i < (battleColors ? 8 : 6); i++) {
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
            update("Palette", false);
            preview.getDef().palette = palette;
            drawPalette(preview.getDef());
        } else {
            System.err.println("Wrong colors count: " + colorsCount);
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
        if (def.palette != null) {
            drawPalette(def);
        }
        putHistory(def, action);
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

    private void highlightColor(int color) {
        System.out.println("0x" + Integer.toHexString(color).toUpperCase());
        preview.setTransformation(image -> {
            int multiply = 8;
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
                        scanline[dst + multiply] = 0xff000000;
                        scanline[dst + 1] = 0xff000000;
                    }
                }
                dy++;
                for (int i = 0; i < multiply - 2; i++, dy++) {
                    result.getPixelWriter().setPixels(0, dy, scanline.length, 1, format, scanline, 0, scanline.length);
                }
                for (int i = 0; i < scanline.length; i++) {
                    boolean highlight = color == scanline[i];
                    if (highlight) {
                        scanline[i] = 0xff000000;
                    }
                }
                result.getPixelWriter().setPixels(0, dy++, scanline.length, 1, format, scanline, 0, scanline.length);
                result.getPixelWriter().setPixels(0, dy - multiply, scanline.length, 1, format, scanline, 0, scanline.length);
            }
            return result;
        });
    }

    private void removeHighlight() {
        preview.setTransformation(Function.identity());
    }

    private static Node groupButtons(Control... nodes) {
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

    private static <T extends Region> T pad(int padding, T node) {
        node.setPadding(new Insets(padding));
        return node;
    }

    private static <T extends Region> T border(Color color, T node) {
        node.setBorder(new Border(new BorderStroke(color, BorderStrokeStyle.SOLID, null, BorderStroke.THIN)));
        return node;
    }

    private static <T extends Region> T bg(Color color, T node) {
        node.setBackground(new Background(new BackgroundFill(color, null, null)));
        return node;
    }

    private static Node border(Color color, Node node) {
        return new VBox(new HBox(border(color, new StackPane(node))));
    }

    private static <T extends Node> T grow(T node) {
        VBox.setVgrow(node, Priority.ALWAYS);
        HBox.setHgrow(node, Priority.ALWAYS);
        return node;
    }

    private static <T extends Node> T growH(T node) {
        HBox.setHgrow(node, Priority.ALWAYS);
        return node;
    }

    private static HBox line(Node... nodes) {
        return line(2, nodes);
    }

    private static HBox line(int padding, Node... nodes) {
        return line(padding, 8, nodes);
    }

    private static HBox line(int padding, int spacing, Node... nodes) {
        HBox box = pad(padding, new HBox(spacing, nodes));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static <T extends Region> T width(int w, T node) {
        node.setPrefWidth(w);
        return node;
    }

    private static <T extends Region> T height(int h, T node) {
        node.setPrefHeight(h);
        return node;
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
