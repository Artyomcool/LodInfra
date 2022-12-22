package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.data.Helpers;
import com.github.artyomcool.lodinfra.data.JsonSerializer;
import com.github.artyomcool.lodinfra.data.dto.*;
import com.jfoenix.controls.JFXChipView;
import impl.org.controlsfx.skin.SearchableComboBoxSkin;
import javafx.beans.binding.Bindings;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.skin.TitledPaneSkin;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.controlsfx.control.textfield.CustomTextField;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class UiPresentation {

    private static final Insets padding = new Insets(2, 2, 2, 2);

    private final Config config;
    private final Data data;
    private final JsonSerializer json;
    private final String lang;
    private Stage primaryStage;
    private StackPane root;

    public UiPresentation(Config config, Data data, JsonSerializer json, String lang) {
        this.config = config;
        this.data = data;
        this.json = json;
        this.lang = lang;
    }

    public void initTabs(StackPane root, Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.root = root;

        TabPane tabs = new TabPane();
        root.getChildren().removeIf(n -> n instanceof TabPane);
        root.getChildren().add(0, tabs);
        for (TabGroup tabGroup : config.tabs) {
            Tab tab = getTab(tabGroup);
            tabs.getTabs().add(tab);
        }
    }

    private <T extends Region> T grow(T node) {
        HBox.setHgrow(node, Priority.ALWAYS);
        VBox.setVgrow(node, Priority.ALWAYS);
        node.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return node;
    }

    private TitledPane pane(String text, Node content) {
        TitledPane pane = new TitledPane(text, content);
        pane.setAnimated(false);
        return pane;
    }

    private Tab getTab(TabGroup tabGroup) {
        Tab tab = new Tab(tabGroup.name.get(lang));
        tab.setClosable(false);

        HBox hBox = new HBox();
        hBox.setFillHeight(true);

        VBox listParent = new VBox();
        listParent.setPrefWidth(250);
        listParent.setMaxWidth(Double.MAX_VALUE);

        StackPane parent = new StackPane();
        tab.setContent(new HBox(listParent, grow(parent)));

        if (tabGroup.type == TabType.multiple) {
            List<Entry> entries = getEntries(data, tabGroup);
            ListView<Entry> list = getListView(tab, parent, entries);
            listParent.getChildren().add(grow(list));
        } else if (tabGroup.type == TabType.join) {
            List<Entry> common = new ArrayList<>();
            for (TabGroup group : tabGroup.tabs) {
                if (group.type == TabType.join) {
                    List<Entry> current = new ArrayList<>();
                    for (TabGroup subtab : group.tabs) {
                        current.addAll(getEntries(data, subtab));
                    }
                    String name = group.name.get(lang);
                    if (name == null || name.isEmpty()) {
                        name = group.title;
                    }
                    ListView<Entry> list = getListView(tab, parent, current);
                    TitledPane pane = new TitledPane(name, list);
                    pane.setAnimated(false);
                    listParent.getChildren().add(pane);

                } else {
                    List<Entry> current = getEntries(data, group);
                    if (current.size() <= 4) {
                        common.addAll(current);
                    } else {
                        String name = group.name.get(lang);
                        if (name == null || name.isEmpty()) {
                            name = group.title;
                        }
                        ListView<Entry> list = getListView(tab, parent, current);
                        TitledPane pane = new TitledPane(name, list);
                        pane.setAnimated(false);
                        listParent.getChildren().add(pane);
                    }
                }
            }
            if (common.size() > 0) {
                if (listParent.getChildren().size() > 0) {
                    ListView<Entry> list = getListView(tab, parent, common);
                    listParent.getChildren().add(pane("Other", list));
                } else {
                    ListView<Entry> list = getListView(tab, parent, common);
                    listParent.getChildren().add(grow(list));
                }
            }
        }

        return tab;
    }

    private List<Entry> getEntries(Data data, TabGroup tabGroup) {
        try {
            List<Entry> result = new ArrayList<>();

            List<DataEntry> dataEntries = data.computeIfAbsent(tabGroup.id, k -> new ArrayList<>());
            for (DataEntry obj : dataEntries) {
                class E extends Entry {
                    E(DataEntry data) {
                        super(tabGroup, data);
                    }
                    @Override
                    public void remove() {
                        dataEntries.removeIf(o -> data == o);
                    }

                    @Override
                    public Entry copy() {
                        DataEntry mm = json.deepCopy(data);
                        dataEntries.add(mm);
                        return new E(mm);
                    }
                }

                result.add(new E(obj));
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException("Tab: " + tabGroup.id, e);
        }
    }

    private ListView<Entry> getListView(Tab tab, StackPane parent, List<Entry> entries) {
        String oldName = tab.getText();
        ListView<Entry> listView = new ListView<>();
        listView.getItems().addAll(entries);

        listView.setEditable(true);
        listView.setCellFactory(l -> new ListCell<>() {
            private TextField textField;
            {
                this.getStyleClass().add("text-field-list-cell");
            }

            @Override
            public void startEdit() {
                super.startEdit();
                if (this.isEditing()) {
                    if (textField == null) {
                        textField = new TextField();
                        textField.setOnAction((a) -> {
                            commitEdit(getItem());
                            a.consume();
                        });
                        textField.setOnKeyReleased((e) -> {
                            if (e.getCode() == KeyCode.ESCAPE) {
                                cancelEdit();
                                e.consume();
                            }
                        });
                    }

                    textField.setText((String) getItem().data.get("id"));
                    setText(null);
                    setGraphic(textField);
                    textField.requestFocus();
                    textField.selectAll();
                }
            }

            @Override
            public void commitEdit(Entry entry) {
                if (isEditing()) {
                    super.commitEdit(entry);
                    entry.data.put("id", textField.getText());
                    getItem().invalidateTitle();
                    setText(getItem().title);
                    setGraphic(null);
                }
            }

            @Override
            public void cancelEdit() {
                if (isEditing()) {
                    super.cancelEdit();
                    setText(getItem().title);
                    setGraphic(null);
                }
            }

            @Override
            protected void updateItem(Entry entry, boolean b) {
                super.updateItem(entry, b);
                if (isEditing()) {
                    textField.setText((String) getItem().data.get("id"));
                } else {
                    setText(getItem() == null ? null : getItem().title);
                }
                if (entry != null) {
                    applyDirty(this, !entry.data.dirty.isEmpty());
                }
            }
        });

        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(actionEvent -> {
            int index = listView.getSelectionModel().getSelectedIndex();
            Entry entry = listView.getItems().get(index);
            entry.remove();
            listView.getItems().remove(index);
        });

        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(actionEvent -> {
            int index = listView.getSelectionModel().getSelectedIndex();
            Entry entry = listView.getItems().get(index);
            listView.getItems().add(entry.copy());
        });

        MenuItem raw = new MenuItem("Edit raw");
        raw.setOnAction(a -> {
            int index = listView.getSelectionModel().getSelectedIndex();
            Entry entry = listView.getItems().get(index);
            Context context = new Context(config, data, entry.data);

            parent.getChildren().clear();

            Button okButton = new Button("ok");
            Button cancelButton = new Button("cancel");

            HBox buttons = new HBox();
            buttons.getChildren().setAll(okButton, cancelButton);
            buttons.getStyleClass().add("tiny-button-bar");
            buttons.setMaxHeight(20);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            buttons.setPadding(new Insets(2));
            StackPane.setAlignment(buttons, Pos.TOP_RIGHT);

            TextArea area = new TextArea();
            area.setText(json.elementToText(context.data));

            parent.getChildren().setAll(area, buttons);

            cancelButton.setOnAction(e -> {
                showItem(tab, parent, oldName, listView, entry);
                e.consume();
            });
            okButton.setOnAction(e -> {
                context.apply("", json.elementFromText(area.getText()));
                showItem(tab, parent, oldName, listView, entry);
                e.consume();
            });
        });

        listView.setContextMenu(new ContextMenu(copy, delete, raw));
        listView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    showItem(tab, parent, oldName, listView, newValue);
                }
        );
        return listView;
    }

    private void showItem(Tab tab, StackPane parent, String oldName, ListView<Entry> listView, Entry newValue) {
        Context context = new Context(config, data, newValue.data);
        context.registerDirty(d -> {
            int index = listView.getSelectionModel().getSelectedIndex();
            ObservableList<Entry> items = listView.getItems();
            items.set(index, items.get(index));

            if (d) {
                data.dirty = true;
                tab.setText(oldName + "*");
                String title = primaryStage.getTitle();
                if (!title.endsWith("*")) {
                    primaryStage.setTitle(title + "*");
                }
            }
        });
        parent.getChildren().clear();

        Parent content = parseTab(newValue.group, context);
        parent.getChildren().add(content);
        finishContext(context);

        root.getProperties().put("currentContext", context);
    }

    private void finishContext(Context context) {
        for (Context.DelayedNode delayedNode : context.delayedNodes) {
            ChangeListener<Object> changed = new ChangeListener<>() {

                final Map<Field, List<Node>> cached = new HashMap<>();

                @Override
                public void changed(ObservableValue<?> observable, Object oldValue, Object newValue) {
                    delayedNode.parent.getChildren().clear();

                    String val = String.valueOf(newValue);
                    Field field = delayedNode.field.cases.get(val);
                    if (field == null) {
                        delayedNode.parent.layout();
                        return;
                    }
                    delayedNode.restore();
                    List<Node> p = cached.computeIfAbsent(field, f -> parse(f, context));
                    delayedNode.parent.getChildren().addAll(p);
                    delayedNode.parent.layout();
                }
            };
            ObservableValue<?> field = context.refs.get(delayedNode.field.dependencyField);
            changed.changed(field, null, field.getValue());
            field.addListener(changed);
        }
    }

    private Parent parseTab(TabGroup tabGroup, Context context) {
        Parent content = parse(tabGroup.fields, context);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        content = scrollPane;

        scrollPane.getStyleClass().add("child-content");

        return content;
    }

    String localString(LocalizedString localizedString, Context context) {
        if (localizedString.containsKey("link")) {
            Map<String, Object> params = context.params();
            LocalizedString link = (LocalizedString) Helpers.interpreter.eval(localizedString.get("link"), params);
            return link.get(lang);
        }
        String result = localizedString.get(lang);
        if (result == null) {
            return "";
        }
        if (result.contains("${")) {
            return Helpers.evalString(result, context.params());
        }
        return result;
    }

    private List<Node> parse(Field field, Context context) {
        context.push(field);
        String path = String.join("/", context.path);
        try {
            switch (field.type) {
                case nothing:
                    return Collections.emptyList();
                case struct: {
                    Field link = context.structs().get(field.link);
                    if (link == null) {
                        throw new IllegalArgumentException("No such ref: " + field.link);
                    }
                    return parse(link, context);
                }
                case array:
                    return parseArray(field, context);
                case group:
                    return parseGroup(field, context);
                case string:
                    return parseString(field, context);
                case integer:
                    return parseInteger(field, context);
                case text:
                    return parseText(field, context);
                case hex:
                    return parseHex(field, context);
                case chips:
                    return parseChips(field, context);
                case combo:
                    return parseCombo(field, context);
                case dependent: {
                    StackPane node = new StackPane();
                    context.delay(node, field);
                    registerDirty(context, node);
                    return Collections.singletonList(node);
                }
            }
            throw new IllegalStateException("Unknown field type: " + field.type);
        } catch (Exception e) {
            throw new RuntimeException("Field: " + path, e);
        } finally {
            context.pop(field);
        }
    }

    private List<Node> parseCombo(Field field, Context context) {

        ComboBox<Value> combo = new ComboBox<>();
        combo.getItems().setAll(getItems(field.link, field.values));

        context.ref(field.id, Bindings.createStringBinding(
                () -> combo.getValue() == null ? "null" : combo.getValue().value(),
                combo.valueProperty()
        ));
        StringProperty property = new SimpleStringProperty();
        property.bindBidirectional(combo.valueProperty(), new StringConverter<>() {
            @Override
            public String toString(Value object) {
                return object == null ? null : object.value();
            }

            @Override
            public Value fromString(String string) {
                for (Value item : combo.getItems()) {
                    if (Objects.equals(String.valueOf(item.value()), string)) {
                        return item;
                    }
                }
                return null;
            }
        });
        context.addProperty(property);

        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Value object) {
                return object == null ? null : object.value() + ". " + object;
            }

            @Override
            public Value fromString(String string) {
                return null;
            }
        });

        if (combo.getItems().size() > 8) {
            SearchableComboBoxSkin<Value> value = new SearchableComboBoxSkin<>(combo);
            CustomTextField node = (CustomTextField) value.getChildren().get(1);
            node.setLeft(null);
            combo.setSkin(value);
        }

        VBox pane = new VBox(
                new Label(localString(field.name, context)),
                combo
        );
        applyMaxWidthPrefWidth(pane, field.maxWidth);
        pane.setPadding(padding);
        registerDirty(context, pane);

        return Collections.singletonList(pane);
    }

    private List<Node> parseChips(Field field, Context context) {
        VBox pane = new VBox();
        Label label = new Label(localString(field.name, context));
        pane.getChildren().add(label);

        JFXChipView<Value> textField = new JFXChipView<>() {
            @Override
            public Orientation getContentBias() {
                return Orientation.HORIZONTAL;
            }
        };
        textField.setSkin(new JFXChipViewSkin<>(textField));
        List<Value> fieldValues = getItems(field.link, field.values);
        textField.getSuggestions().addAll(fieldValues);
        textField.setPredicate((value, s) -> value.toString().toLowerCase().startsWith(s.toLowerCase()) && !textField.getChips().contains(value));
        applyMaxWidthPrefWidth(pane, field.maxWidth);
        StringProperty property = new SimpleStringProperty();
        class Listener implements ListChangeListener<Value>, ChangeListener<String> {
            boolean updating = false;

            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if (updating) {
                    return;
                }
                updating = true;
                try {
                    Set<String> values = new HashSet<>(Arrays.asList(newValue.substring("bits:".length()).split(", ")));
                    List<Value> data = fieldValues.stream().filter(v -> values.contains(v.value())).collect(Collectors.toList());
                    textField.getChips().setAll(data);
                } finally {
                    updating = false;
                }
            }

            @Override
            public void onChanged(Change<? extends Value> c) {
                if (updating) {
                    return;
                }
                updating = true;
                try {
                    String data = textField.getChips().stream().map(Value::value).collect(Collectors.joining(", ", "bits:", ""));
                    property.setValue(data);
                } finally {
                    updating = false;
                }
            }
        }

        Listener listener = new Listener();
        textField.getChips().addListener(listener);
        property.addListener(listener);

        context.addProperty(property);
        pane.setPrefHeight(1);
        pane.getChildren().add(textField);
        pane.setPadding(padding);
        registerDirty(context, pane);
        return Collections.singletonList(pane);
    }

    private List<Node> parseHex(Field field, Context context) {
        VBox pane = new VBox();
        Label label = new Label(localString(field.name, context));
        TextArea textField = new TextArea();
        textField.setWrapText(true);
        textField.setFont(Font.font("monospace"));
        if (field.id != null) {
            context.refs.put(field.id, textField.textProperty());
        }
        textField.setPrefColumnCount(32);


        Property<Object> obj = context.createRawProperty();
        textField.textProperty().bindBidirectional(obj, new StringConverter<Object>() {
            @Override
            public String toString(Object object) {
                if (object == null) {
                    return "<NULL>";
                }
                List<Integer> lst = (List)object;
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < lst.size(); i++) {
                    result.append(Integer.toHexString(i));
                }
                return result.toString();
            }

            @Override
            public Object fromString(String string) {
                if (string.equals("<NULL>")) {
                    return null;
                }
                List<Integer> t = new ArrayList<>();
                for (int i = 0; i < string.length(); i += 2) {
                    int c = Integer.parseUnsignedInt(string, i, i + 2, 16);
                    t.add(c);
                }
                return t;
            }
        });

        textField.setPrefRowCount(8);
        pane.getChildren().add(label);
        pane.getChildren().add(textField);
        pane.setPadding(padding);

        registerDirty(context, pane);

        return Collections.singletonList(pane);
    }

    private List<Node> parseText(Field field, Context context) {
        VBox pane = new VBox();
        Label label = new Label(localString(field.name, context));
        TextArea textField = new TextArea();
        textField.setWrapText(true);
        if (field.id != null) {
            context.refs.put(field.id, textField.textProperty());
        }
        applyWidthSample(textField, field.widthSample);
        applyMaxWidth(pane, field.maxWidth);
        context.addProperty(textField.textProperty());
        textField.setPrefRowCount("tiny".equals(field.option) ? 1 : 3);
        pane.getChildren().add(label);
        pane.getChildren().add(textField);
        pane.setPadding(padding);
        label.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2) {
                if (textField.getPrefRowCount() == ("tiny".equals(field.option) ? 1 : 3)) {
                    textField.setPrefRowCount(12);
                    if (field.maxWidth != 0) {
                        pane.setMaxWidth(field.maxWidth * 2);
                    }
                    if (field.widthSample != null) {
                        textField.setPrefColumnCount(field.widthSample.length() * 2);
                    }
                } else {
                    if (field.maxWidth != 0) {
                        pane.setMaxWidth(field.maxWidth);
                    }
                    if (field.widthSample != null) {
                        textField.setPrefColumnCount(field.widthSample.length());
                    }
                    textField.setPrefRowCount("tiny".equals(field.option) ? 1 : 3);
                }
            }
        });
        registerDirty(context, pane);
        return Collections.singletonList(pane);
    }

    private List<Node> parseInteger(Field field, Context context) {
        VBox pane = new VBox();
        Label label = new Label(localString(field.name, context));
        Spinner<Integer> textField = new Spinner<>(field.min, field.max, field.min);
        textField.setEditable(true);
        textField.setPrefWidth(field.widthSample == null ? 100 : field.widthSample.length() * 12 + 40);
        if (field.id != null) {
            context.refs.put(field.id, textField.valueProperty());
        }
        if (field.maxWidth != 0) {
            pane.setMaxWidth(field.maxWidth);
        }
        context.addProperty(textField.getEditor().textProperty());
        pane.getChildren().add(label);
        pane.getChildren().add(textField);
        pane.setPadding(padding);
        registerDirty(context, pane);
        return Collections.singletonList(pane);
    }

    private List<Node> parseString(Field field, Context context) {
        VBox pane = new VBox();
        Label label = new Label(localString(field.name, context));
        TextField textField = new TextField();
        if (field.widthSample != null) {
            textField.setPrefColumnCount(field.widthSample.length());
        }
        if (field.id != null) {
            context.refs.put(field.id, textField.textProperty());
        }
        if (field.maxWidth != 0) {
            pane.setMaxWidth(field.maxWidth);
        }
        context.addProperty(textField.textProperty());
        pane.getChildren().add(label);
        pane.getChildren().add(textField);
        pane.setPadding(padding);
        registerDirty(context, pane);
        return Collections.singletonList(pane);
    }

    private List<Node> parseGroup(Field field, Context context) {
        if ("tiny".equals(field.option) || "tinyNoWrap".equals(field.option)) {
            StackPane pane = new StackPane();
            pane.getStyleClass().add("bordered-titled-border");
            pane.setPadding(padding);

            Pane content = "tiny".equals(field.option) ? new FlowPane() : new HBox();
            for (Field f : field.fields) {
                content.getChildren().addAll(parse(f, context));
            }
            content.getStyleClass().add("bordered-titled-content");
            Label label = new Label();
            label.setText(localString(field.name, context));
            label.setLabelFor(content);
            label.getStyleClass().add("bordered-titled-title");
            label.setContentDisplay(ContentDisplay.RIGHT);

            StackPane.setAlignment(label, Pos.TOP_CENTER);
            pane.getChildren().setAll(content, label);
            if (field.maxWidth != 0) {
                pane.setMaxWidth(field.maxWidth);
            }

            if (field.id != null) {
                label.setGraphic(
                        createButtons(
                                context,
                                200,
                                textArea -> pane.getChildren().set(0, textArea == null ? content : textArea)
                        )
                );
            }
            registerDirty(context, pane);
            return Collections.singletonList(pane);
        }
        if ("line".equals(field.option)) {
            HBox content = new HBox();
            for (Field f : field.fields) {
                content.getChildren().addAll(parse(f, context));
            }
            registerDirty(context, content);
            return Collections.singletonList(content);
        }
        if ("simple".equals(field.option)) {
            List<Node> result = new ArrayList<>();
            for (Field f : field.fields) {
                List<Node> parse = parse(f, context);
                for (Node node : parse) {
                    node.getProperties().put("field", f);
                    result.add(node);
                }
            }
            return result;
        }

        VBox box = parse(field.fields, context);

        TitledPane pane = new TitledPane(localString(field.name, context), box);
        pane.setAnimated(false);

        TitledPaneSkin skin = new TitledPaneSkin(pane) {
            /**
             * {@inheritDoc}
             */
            @Override
            protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
                // fixes bug of using padding in computation
                return super.computePrefHeight(width - rightInset - leftInset, topInset, rightInset, bottomInset, leftInset);
            }
        };
        if (field.id != null) {
            BorderPane titlePane = new BorderPane();
            titlePane.setLeft(new Label(pane.getText()));
            titlePane.setCenter(new Label(" "));
            pane.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            pane.setGraphic(titlePane);

            titlePane.setRight(
                    createButtons(
                            context,
                            300,
                            textArea -> pane.setContent(textArea == null ? box : textArea)
                    )
            );
        }
        pane.setSkin(skin);
        pane.setCollapsible(field.fillWidth);
        if (field.maxWidth != 0) {
            box.setMaxWidth(field.maxWidth);
        }
        if ("grow".equals(field.option)) {
            grow(pane);
        }
        pane.setPadding(padding);
        registerDirty(context, pane);
        return Collections.singletonList(pane);
    }

    private Node createButtons(Context context, int minHeight, Consumer<TextArea> showHide) {

        Button rawButton = new Button("e");
        Button copyButton = new Button("c");
        Button pasteButton = new Button("p");
        Button okButton = new Button("ok");
        Button cancelButton = new Button("cancel");

        HBox buttons = new HBox();
        buttons.getChildren().setAll(rawButton, copyButton, pasteButton);
        buttons.getStyleClass().add("tiny-button-bar");

        TextArea area = new TextArea();
        area.setMinHeight(minHeight);
        String path = context.path();
        rawButton.setOnAction(e -> {
            showHide.accept(area);
            buttons.getChildren().setAll(okButton, cancelButton);
            area.setText(json.elementToText(context.get(path)));
            e.consume();
        });
        cancelButton.setOnAction(e -> {
            showHide.accept(null);
            buttons.getChildren().setAll(rawButton, copyButton, pasteButton);
            e.consume();
        });
        okButton.setOnAction(e -> {
            showHide.accept(null);
            context.apply(path, json.elementFromText(area.getText()));
            buttons.getChildren().setAll(rawButton, copyButton, pasteButton);
            e.consume();
        });
        copyButton.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(json.elementToText(context.get(path)));
            clipboard.setContent(content);
        });
        pasteButton.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            context.apply(path, json.elementFromText(clipboard.getString()));
        });

        return buttons;
    }

    public void registerDirty(Context context, Node node) {
        context.registerDirty(d -> applyDirty(node, d));
    }

    private static void applyDirty(Node node, boolean d) {
        if (d) {
            if (!node.getStyleClass().contains("dirty")) {
                node.getStyleClass().add("dirty");
            }
        } else {
            node.getStyleClass().remove("dirty");
        }
    }

    private List<Node> parseArray(Field field, Context context) {
        Matcher matcher = Helpers.ARRAY.matcher(field.link);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Wrong array field");
        }
        Field link = matcher.group(1).isEmpty() ? null : context.structs().get(matcher.group(1));
        if (!matcher.group(1).isEmpty() && link == null) {
            throw new IllegalArgumentException("No such ref: " + matcher.group(1));
        }
        String[] group = matcher.group(2).split(",");
        String countGroup = Helpers.evalString(group[group.length - 1], context.params());
        boolean allowAdd;
        int count;
        if (countGroup.isEmpty()) {
            List<Object> contextValue = (List<Object>) context.currentValue();
            count = contextValue.size();
            allowAdd = true;
        } else {
            count = Integer.parseInt(countGroup);
            allowAdd = false;
        }
        Context.State savedState = context.state();
        int start = group.length > 1 ? Integer.parseInt(group[0]) : 0;
        if ("long".equals(field.option)) {
            List<Context.State> data = new ArrayList<>();
            for (int j = 0, i = start; i < count; i++, j++) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("i", (long) i);
                vars.put("j", (long) j);
                context.push(String.valueOf(i), vars);
                data.add(context.state());
                context.pop();
            }

            ListView<Context.State> listView = new ListView<>(FXCollections.observableList(data));
            VBox.setVgrow(listView, Priority.ALWAYS);
            listView.setCellFactory(p -> new ListCell<>() {
                @Override
                protected void updateItem(Context.State item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        return;
                    }

                    VBox group = new VBox();
                    if (field.fields != null) {
                        for (Field f : field.fields) {
                            Context.State state = item.swap();
                            List<Node> parse = parse(f, context);
                            state.restore();
                            for (Node node : parse) {
                                node.getProperties().put("field", f);
                                group.getChildren().add(node);
                            }
                        }
                    }
                    if (link != null) {
                        Context.State state = item.swap();
                        List<Node> parse = parse(link, context);
                        state.restore();
                        for (Node node : parse) {
                            node.getProperties().put("field", link);
                            group.getChildren().add(node);
                        }
                    }
                    setGraphic(group);
                }
            });
            List<Node> r = new ArrayList<>();
            r.add(listView);
            if (allowAdd) {
                Button e = new Button();
                e.setText("+");
                e.setOnAction(a -> {
                    Map<String, Object> vars = new HashMap<>();
                    int i = listView.getItems().size() + start;
                    vars.put("i", (long) i);
                    vars.put("j", (long) listView.getItems().size());
                    Context.State swap = savedState.swap();
                    context.push(String.valueOf(i), vars);
                    context.set(new HashMap<>());
                    listView.getItems().add(context.state());
                    swap.restore();
                });

                Button q = new Button();
                q.setText("-");

                ButtonBar bar = new ButtonBar();
                Field f = new Field();
                f.fillWidth = true;
                bar.getButtons().add(e);
                bar.getButtons().add(q);
                bar.getProperties().put("field", f);
                bar.setPadding(padding);
                r.add(bar);
            }
            registerDirty(context, listView);
            return r;
        } else {
            List<Node> r = new ArrayList<>(count);
            for (int j = 0, i = start; i < count; i++, j++) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("i", (long) i);
                vars.put("j", (long) j);
                context.push(String.valueOf(i), vars);
                if (field.fields != null) {
                    for (Field f : field.fields) {
                        List<Node> parse = parse(f, context);
                        for (Node node : parse) {
                            node.getProperties().put("field", f);
                            r.add(node);
                        }
                    }
                }
                if (link != null) {
                    List<Node> parse = parse(link, context);
                    for (Node node : parse) {
                        node.getProperties().put("field", link);
                        r.add(node);
                    }
                }
                context.pop();
            }
            if (allowAdd) {
                List<Object> contextValue = (List<Object>) context.currentValue();
                Button e = new Button();
                e.setText("+");
                e.setOnAction(a -> contextValue.add(new HashMap<>()));

                Button q = new Button();
                q.setText("-");

                ButtonBar bar = new ButtonBar();
                Field f = new Field();
                f.fillWidth = true;
                bar.getButtons().add(e);
                bar.getButtons().add(q);
                bar.getProperties().put("field", f);
                bar.setPadding(padding);
                r.add(bar);
            }
            return r;
        }
    }

    private FlowPane createFlow() {
        FlowPane currentFlow = new FlowPane();
        currentFlow.setPadding(padding);
        currentFlow.setPrefWrapLength(800);
        return currentFlow;
    }

    private VBox parse(List<Field> fields, Context context) {
        VBox content = new VBox();
        FlowPane currentFlow = createFlow();

        for (Field field : fields) {
            List<Node> parse = parse(field, context);
            if (parse.isEmpty() && field.breaks) {
                content.getChildren().add(currentFlow);
                currentFlow = createFlow();
                continue;
            }
            for (Node node : parse) {
                Field f = (Field) node.getProperties().get("field");
                if (f == null) {
                    f = field;
                }
                if (VBox.getVgrow(node) == Priority.ALWAYS || f.fillWidth || field.fillWidth) {
                    if (!currentFlow.getChildren().isEmpty()) {
                        content.getChildren().add(currentFlow);
                        currentFlow = createFlow();
                    }
                    content.getChildren().add(node);
                } else {
                    currentFlow.getChildren().add(node);
                    if (f.breaks || field.breaks) {
                        content.getChildren().add(currentFlow);
                        currentFlow = createFlow();
                    }
                }
            }
        }
        if (!currentFlow.getChildren().isEmpty()) {
            content.getChildren().add(currentFlow);
        }
        return content;
    }

    private List<Value> getItems(String link, List<Value> values) {
        List<Value> result = new ArrayList<>(values == null ? Collections.emptyList() : values);
        if (link != null && !link.isEmpty()) {
            result.addAll(config.enums.getOrDefault(link, Collections.emptyList()));
            result.addAll(config.dynamicEnums(data).getOrDefault(link, Collections.emptyList()));
        }
        return result;
    }

    private void applyMaxWidthPrefWidth(Region region, int maxWidth) {
        if (maxWidth != 0) {
            region.setMaxWidth(maxWidth);
            region.setPrefWidth(maxWidth);
        }
    }

    private void applyWidthSample(TextArea textField, String widthSample) {
        if (widthSample != null) {
            textField.setPrefColumnCount(widthSample.length());
        }
    }

    private void applyMaxWidth(Region pane, int maxWidth) {
        if (maxWidth != 0) {
            pane.setMaxWidth(maxWidth);
        }
    }


}
