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

    public Gui(Path cfg) throws IOException {
        this.config = json.loadConfig(cfg);
        this.format = config.formats.get("dat");
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            primaryStage.setTitle("Data Editor");
            StackPane root = new StackPane();

            root.getChildren().add(new TabPane(createLastFiles(root)));

            MenuButton fileMenu = new MenuButton("File");

            MenuItem save = fileChooserMenu(
                    primaryStage,
                    false,
                    "json",
                    "Ctrl+S",
                    this::saveJson
            );
            fileMenu.getItems().add(save);

            MenuItem open = fileChooserMenu(
                    primaryStage,
                    true,
                    "json",
                    "Ctrl+O",
                    file -> loadJson(root, file)
            );
            fileMenu.getItems().add(open);

            MenuItem export = fileChooserMenu(
                    primaryStage,
                    false,
                    "dat",
                    "Shift+Ctrl+S",
                    this::saveDat
            );
            fileMenu.getItems().add(export);

            MenuItem _import = fileChooserMenu(
                    primaryStage,
                    true,
                    "dat",
                    "Shift+Ctrl+O",
                    file -> loadDat(root, file)
            );
            fileMenu.getItems().add(_import);

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

    private void saveJson(Path file) throws IOException {
        json.saveData(data, file);
    }

    private void loadJson(StackPane root, Path file) throws IOException {
        data = json.loadData(file);

        initTabs(root);
    }

    private void saveDat(Path file) throws IOException {
        Object serialized = new HashMap<>();

        allTabs().forEach(tabGroup -> {
            Object data = this.data.get(tabGroup.id);

            Alias alias = format.aliases.get(tabGroup.alias);
            new AliasProcessor(format.processors, alias).write(serialized, Collections.emptyMap(), data);
        });

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file))
        )) {
            FormatParser.write(out, serialized, format.template, format.structs);
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
                data.put(tab.id, entries.stream().map(DataEntry::new).toList());
            });
            initTabs(root);
        }
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

    private MenuItem fileChooserMenu(Stage primaryStage, boolean open, String ext, String accelerator, FileHandler callback) {
        String title = open ? "Open " + ext.toUpperCase(Locale.ROOT) : "Save " + ext.toUpperCase(Locale.ROOT);
        MenuItem item = new MenuItem(title);
        item.setAccelerator(KeyCombination.valueOf(accelerator));
        item.setOnAction(event -> {
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
        });
        return item;
    }

    private Stream<TabGroup> allTabs() {
        return allTabs(config.tabs);
    }

    private Stream<TabGroup> allTabs(List<TabGroup> tabs) {
        return tabs.stream().flatMap(t -> t.type == TabType.join ? allTabs(t.tabs) : Stream.of(t));
    }

    private void initTabs(StackPane root) {
        new UiPresentation(config, data, json, lang).initTabs(root);
    }

    private interface FileHandler {
        void handle(Path file) throws IOException;
    }

}