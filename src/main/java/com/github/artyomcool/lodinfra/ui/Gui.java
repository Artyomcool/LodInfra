package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.data.AliasProcessor;
import com.github.artyomcool.lodinfra.data.FormatParser;
import com.github.artyomcool.lodinfra.data.Helpers;
import com.github.artyomcool.lodinfra.data.JsonSerializer;
import com.github.artyomcool.lodinfra.data.dto.*;
import com.jfoenix.controls.JFXChipView;
import impl.org.controlsfx.skin.SearchableComboBoxSkin;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.skin.TitledPaneSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.controlsfx.control.textfield.CustomTextField;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Gui extends Application {

    private String lang = "Ru";
    private JsonSerializer json = new JsonSerializer(lang);

    final Config config;
    final Format format;
    Data data;
    Preferences prefs = Preferences.userRoot().node(this.getClass().getName());

    Insets padding = new Insets(2, 2, 2, 2);
    private Stage primaryStage;

    public Gui(Path cfg) throws IOException {
        System.out.println(cfg);
        this.config = json.loadConfig(cfg);
        this.format = config.formats.get("dat");
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        try {
            primaryStage.setTitle("Data Editor");
            StackPane root = new StackPane();

            root.getChildren().add(new TabPane(createLastFiles(root)));

            MenuButton fileMenu = new MenuButton("File");

            fileMenu.getItems().add(createMenuItem(
                    "Undo",
                    "Ctrl+Alt+Z",
                    () -> doWithContext(root, Context::undo)
            ));

            fileMenu.getItems().add(new SeparatorMenuItem());

            fileMenu.getItems().add(fileChooserMenu(
                    primaryStage,
                    false,
                    "json",
                    "Ctrl+S",
                    file -> saveJson(root, file)
            ));

            fileMenu.getItems().add(fileChooserMenu(
                    primaryStage,
                    true,
                    "json",
                    "Ctrl+O",
                    file -> loadJson(root, file)
            ));

            fileMenu.getItems().add(fileChooserMenu(
                    primaryStage,
                    false,
                    "dat",
                    "Shift+Ctrl+S",
                    file -> saveDat(root, file)
            ));

            fileMenu.getItems().add(fileChooserMenu(
                    primaryStage,
                    true,
                    "dat",
                    "Shift+Ctrl+O",
                    file -> loadDat(root, file)
            ));

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());

            StackPane.setAlignment(fileMenu, Pos.TOP_LEFT);
            StackPane.setMargin(fileMenu, padding);
            root.getChildren().add(fileMenu);

            primaryStage.setMinWidth(1024);
            primaryStage.setMinHeight(800);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record LastItem(String path, boolean open) {
        @Override
        public String toString() {
            return (open ? "Open JSON " : "Open DAT ") + path;
        }
    }

    private Tab createLastFiles(StackPane root) {
        Tab files = new Tab("Last files");
        files.setClosable(true);
        ListView<LastItem> list = new ListView<>();

        VBox listParent = new VBox(list);
        for (String json : prefs.get("last-files-json", "").split(",")) {
            if (!json.equals("")) {
                list.getItems().add(new LastItem(json, true));
            }
        }
        for (String dat : prefs.get("last-files-dat", "").split(",")) {
            if (!dat.equals("")) {
                list.getItems().add(new LastItem(dat, false));
            }
        }
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(LastItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? null : item.toString());
                if (!empty) {
                    setOnMouseClicked(event -> {
                        try {
                            if (item.open) {
                                loadJson(root, new File(item.path).toPath());
                            } else {
                                loadDat(root, new File(item.path).toPath());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        files.setContent(listParent);
        return files;
    }

    private void saveJson(StackPane root, Path file) throws IOException {
        json.saveData(data, file);
        primaryStage.setTitle(file.toString());
        cleanTabs(root);
    }

    private void loadJson(StackPane root, Path file) throws IOException {
        data = json.loadData(file);

        initTabs(root);

        primaryStage.setTitle(file.toString());
    }

    private void saveDat(StackPane root, Path file) throws IOException {
        Object serialized = new LinkedHashMap<>();

        Map<String, TabGroup> groupMap = allTabs().collect(Collectors.toMap(t -> t.id, t -> t));
        for (Map.Entry<String, List<DataEntry>> entry : this.data.entrySet()) {
            Alias alias = format.aliases.get(groupMap.get(entry.getKey()).alias);
            new AliasProcessor(format.processors, alias).write(serialized, Collections.emptyMap(), entry.getValue());
        }

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file))
        )) {
            FormatParser.write(out, serialized, format.template, format.structs);
        }

        primaryStage.setTitle(file.toString());
        cleanTabs(root);
    }

    private void cleanTabs(StackPane root) {
        allTabs().forEach(tabGroup -> {
            List<DataEntry> data = this.data.get(tabGroup.id);
            for (DataEntry entry : data) {
                entry.dirty.clear();
            }
        });

        for (Node child : root.getChildren()) {
            if (child instanceof TabPane) {
                for (Tab tab : ((TabPane) child).getTabs()) {
                    String text = tab.getText();
                    if (text.endsWith("*")) {
                        tab.setText(text.substring(0, text.length() - 1));
                    }
                }
            }
        }

        doWithContext(root, Context::cleanUpDirty);
    }

    private void doWithContext(StackPane root, Consumer<Context> consumer) {
        Context context = (Context) root.getProperties().get("currentContext");
        if (context != null) {
            consumer.accept(context);
        }
    }

    private void loadDat(StackPane root, Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file))
        )) {
            Format f = config.formats.get("dat");
            Object p = FormatParser.parse(in, f.template, f.structs);

            data = new Data();
            allTabs().forEach(tab -> {
                Alias alias = format.aliases.get(tab.alias);
                List<Map<String, Object>> entries = new AliasProcessor(format.processors, alias).read(p, config);
                data.put(tab.id, new ArrayList<>(entries.stream().map(DataEntry::new).toList()));
            });
            initTabs(root);
        }

        primaryStage.setTitle(file.toString());
    }

    private FileChooser createFileChooser(String name, String fileExt) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(name);
        fileChooser.setSelectedExtensionFilter(new FileChooser.ExtensionFilter(fileExt + " files", "json"));

        String initial = prefs.get("file-" + fileExt, null);
        if (initial != null) {
            File initialFile = new File(initial);
            fileChooser.setInitialDirectory(initialFile.getParentFile());
            fileChooser.setInitialFileName(initialFile.getName());
        }
        return fileChooser;
    }

    private MenuItem createMenuItem(String name, String shortcut, Runnable callback) {
        MenuItem item = new MenuItem(name);
        item.setAccelerator(KeyCombination.valueOf(shortcut));
        item.setOnAction(a -> callback.run());
        return item;
    }

    private MenuItem fileChooserMenu(Stage primaryStage, boolean open, String ext, String accelerator, FileHandler callback) {
        String title = open ? "Open " + ext.toUpperCase(Locale.ROOT) : "Save " + ext.toUpperCase(Locale.ROOT);
        return createMenuItem(
                title,
                accelerator,
                () -> {
                    FileChooser chooser = createFileChooser(title, ext);
                    File file = open ? chooser.showOpenDialog(primaryStage) : chooser.showSaveDialog(primaryStage);
                    try {
                        callback.handle(file.toPath());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    prefs.put("file-" + ext, file.getAbsolutePath());

                    List<String> lastFiles = new ArrayList<>(Arrays.asList(prefs.get("last-files-" + ext, "").split(",")));
                    lastFiles.remove(file.getAbsolutePath());
                    lastFiles.add(0, file.getAbsolutePath());
                    lastFiles.remove("");

                    prefs.put("last-files-" + ext, String.join(",", lastFiles));
                }
        );
    }

    private Stream<TabGroup> allTabs() {
        return allTabs(config.tabs);
    }

    private Stream<TabGroup> allTabs(List<TabGroup> tabs) {
        return tabs.stream().flatMap(t -> t.type == TabType.join ? allTabs(t.tabs) : Stream.of(t));
    }

    private void initTabs(StackPane root) {
        new UiPresentation(config, data, json, lang).initTabs(root, primaryStage);
    }

    private interface FileHandler {
        void handle(Path file) throws IOException;
    }

}