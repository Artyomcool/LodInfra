package com.github.artyomcool.lodinfra.dateditor.ui;

import com.github.artyomcool.lodinfra.dateditor.grammar.*;
import com.github.artyomcool.lodinfra.ui.JFXChipViewSkin;
import com.github.artyomcool.lodinfra.ui.Ui;
import com.jfoenix.controls.JFXChipView;
import impl.org.controlsfx.skin.SearchableComboBoxSkin;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.skin.TitledPaneSkin;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.controlsfx.control.SearchableComboBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.github.artyomcool.lodinfra.ui.Ui.*;

public class StructUi extends Application {
    private final StructData struct;
    private final DynamicPathMap<Consumer<String>> listeners = new DynamicPathMap<>();
    private final Stack<DynamicPath> currentName = new Stack<>();
    private Map<String, String> translations;
    private RefStorage storage;

    public StructUi(StructData struct) {
        this.struct = struct;
        currentName.push(DynamicPath.ROOT);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        String pn = "C:\\Users\\Raider\\Desktop\\shared\\HotA\\HotA.htd";
        String text = Files.readString(Path.of(pn));
        String translationsText = Files.readString(Path.of("C:\\Users\\Raider\\Desktop\\shared\\HotA\\HotA.htt"));

        translations = Translations.parse(translationsText);
        storage = new HtaParser(text).load();

        StackPane root = new StackPane();
        Declaration declaration = new Declaration(this.struct.meta, this.struct, null, "", null);
        BNode bNode;
        try {
            bNode = structUi(declaration, true);
        } catch (Exception e) {
            throw new RuntimeException("Path: " + currentName().toStaticPath(), e);
        }
        root.getChildren().add(column(0, growV(bNode.node())/*,
                line(
                        button("Print", () -> {
                            StringBuilder builder = new StringBuilder();
                            bNode.data().write(builder, 0);
                            System.out.println(builder);
                        }),
                        button("Generate Parser", () -> {

                        })
                )*/));
        bNode.bind(storage);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());

        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(800);
        primaryStage.setScene(scene);
        primaryStage.setWidth(1024);
        primaryStage.setHeight(800);
        primaryStage.show();
    }

    private DynamicPath currentName() {
        return currentName.peek();
    }

    private BNode structUi(Declaration data, boolean hasExternalTitle) {
        if (data.meta().is("tabhost")) {
            return structUiTabHost(data);
        }
        BNode bNode = structUiFlow(data);
        if (hasExternalTitle) {
            return bNode;
        }
        if (data.meta().is("group:collapsable")) {
            return bNode.replace(groupCollapsable(data, bNode.node(), true));
        }
        return bNode.replace(simpleGroup(data.textName(), bNode.node()));
    }

    private Node groupCollapsable(Declaration data, Node node, boolean collapsible) {
        TitledPane pane = new TitledPane(data.textName(), node);
        pane.setAnimated(false);
        pane.setCollapsible(collapsible);

        TitledPaneSkin skin = new TitledPaneSkin(pane) {
            @Override
            protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
                // fixes bug of using padding in computation
                return super.computePrefHeight(width - rightInset - leftInset, topInset, rightInset, bottomInset, leftInset);
            }
        };
        if (collapsible) {
            Node title = skin.getChildren().get(skin.getChildren().size() - 1);
            title.setOnMouseClicked(event -> {
                if (event.isControlDown()) {
                    Parent parent = pane.getParent();
                    for (Node n : parent.getChildrenUnmodifiable()) {
                        if (n instanceof TitledPane p) {
                            p.setExpanded(event.isAltDown());
                        }
                    }
                    pane.setExpanded(true);
                }
            });
            Tooltip.install(title, new Tooltip("Use Ctrl (+ Alt) + Click to collapse/expand all"));
        }
        pane.setSkin(skin);
        return growH(pane);
    }

    private Node simpleGroup(String labelText, Node node) {
        if (labelText.isEmpty()) {
            return node;
        }
        StackPane pane = pad(new StackPane());
        pane.getStyleClass().add("bordered-titled-border");

        node.getStyleClass().add("bordered-titled-content");
        Label label = new Label(labelText);
        label.setLabelFor(node);
        label.getStyleClass().add("bordered-titled-title");
        label.setContentDisplay(ContentDisplay.RIGHT);

        StackPane.setAlignment(label, Pos.TOP_LEFT);
        pane.getChildren().setAll(node, label);
        return pane;
    }

    private BNode structUiFlow(Declaration data) {
        if (data.meta().is("nowrap") || data.meta().is("nowrap:vertical")) {
            Supplier<? extends Pane> ui = data.meta().is("nowrap:vertical")
                    ? () -> Ui.column(0, 0)
                    : () -> Ui.line(0, 0);
            return structUiCommon(data, ui, (hBox, declaration) -> {
                BNode node = typeUi(declaration, false);
                hBox.getChildren().add(node.node());
                return node;
            });
        }

        return structUiCommon(data, () -> {
            VBox box = column(0, 2);
            return grow(box);
        }, (pane, cdecl) -> {
            BNode node = typeUi(cdecl, false);
            Priority hgrow = HBox.getHgrow(node.node());
            ObservableList<Node> columns = pane.getChildren();
            if (hgrow == Priority.ALWAYS) {
                columns.add(node.node());
            } else {
                Pane addTo = null;
                if (!columns.isEmpty()
                        && !cdecl.meta().is("break")
                        && "!ROW!".equals(columns.get(columns.size() - 1).getUserData())) {
                    HBox line = (HBox) columns.get(columns.size() - 1);
                    if (cdecl.meta().is("vbreak")) {
                        Node last = line.getChildren().get(line.getChildren().size() - 1);
                        if (last instanceof FlowPane f) {
                            line.getChildren().remove(line.getChildren().size() - 1);
                            HBox box = new HBox(0);
                            box.getChildren().addAll(f.getChildren());
                            line.getChildren().add(box);
                        }
                        addTo = new FlowPane(0, 1);
                        addTo.setMinWidth(10);
                        HBox.setHgrow(addTo, Priority.ALWAYS);
                        line.getChildren().add(addTo);
                    } else {
                        Node last = line.getChildren().get(line.getChildren().size() - 1);
                        if (last instanceof FlowPane f) {
                            addTo = f;
                        }
                    }
                }
                if (addTo == null) {
                    addTo = new FlowPane(0, 1) {
                        @Override
                        protected void setWidth(double value) {
                            super.setWidth(value);
                        }
                    };
                    addTo.setMinWidth(10);
                    HBox.setHgrow(addTo, Priority.ALWAYS);
                    HBox line = line(0, 0, addTo);
                    line.setUserData("!ROW!");
                    columns.add(line);
                }
                addTo.getChildren().add(node.node());
            }
            return node;
        });
    }

    private BNode structUiTabHost(Declaration data) {
        return structUiCommon(data, TabPane::new, (tabs, cdecl) -> {
            BNode node = typeUi(cdecl, false);
            Tab tab = new Tab(cdecl.textName(), node.node());
            tab.setClosable(false);
            tabs.getTabs().add(tab);
            return node;
        });
    }

    private <T extends Node> BNode structUiCommon(Declaration data,
                                                  Supplier<T> uiCreator,
                                                  BiFunction<T, Declaration, BNode> appender) {
        T ui = uiCreator.get();
        Map<String, BNode> peaces = new HashMap<>();
        Map<String, Declaration> declarations = new HashMap<>();
        for (Declaration cdata : data.destructure()) {
            String name = cdata.name();
            currentName.push(currentName().append(name));
            BNode node = appender.apply(ui, cdata);
            currentName.pop();
            declarations.put(name, cdata);
            peaces.put(name, node);
        }

        return bind(ui, s -> {
            RefStorage.Struct struct = (RefStorage.Struct) s;
            for (Map.Entry<String, BNode> entry : peaces.entrySet()) {
                String name = entry.getKey();
                RefStorage child = struct.computeIfAbsent(name, k -> declarations.get(name).createDefault());
                entry.getValue().bind(child);
            }
        });
    }

    private BNode genericUi(Declaration data) {
        GenericType genericType = data.asGeneric();
        return switch (genericType.base) {
            case VECTOR -> listUi((DefType) genericType.params.get(0), data, false);
            case BITSET -> bitsetUi(data);
            case ARRAY -> listLineUi(data, true);
            default -> stub();
        };
    }

    private BNode listStructUi(Declaration data, boolean fixedSize) {
        ListView<RefStorage> listView = new ListView<>();
        Declaration elementDefaultDeclaration = data.extractElement();
        String nameSubPath = elementDefaultDeclaration.meta().value("name_path");

        DynamicPath oldName = currentName();
        currentName.push(oldName.append(() -> listView.getSelectionModel().getSelectedIndex() + ""));

        BNode bNode = structUi(elementDefaultDeclaration, true);

        StackPane content = new StackPane();

        listView.setMinWidth(nameSubPath == null ? 40 : 200);
        listView.setPrefWidth(nameSubPath == null ? 40 : 200);
        listView.setPrefHeight(80);
        listView.setCellFactory(l -> new ListCell<>() {
            {
                this.getStyleClass().add("text-field-list-cell");
            }

            @Override
            protected void updateItem(RefStorage item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null) {
                    setText("");
                    return;
                }

                int index = listView.getItems().indexOf(item);
                StringBuilder name = new StringBuilder(index + ".");

                if (nameSubPath == null) {
                    setText(name.toString());
                } else if (nameSubPath.startsWith("translate:")) {
                    String subnameAfterTranslate = nameSubPath.substring("translate:".length());
                    String path = item.childForPath(subnameAfterTranslate).self();
                    if (path == null || path.isEmpty()) {
                        path = oldName.toStaticPath().substring("root.".length()) + "." + name + subnameAfterTranslate;
                    }
                    String str = translations.get(path);
                    setText(name.append(str).toString());
                } else {
                    RefStorage refStorage = item.childForPath(nameSubPath);
                    name.append(refStorage.self());

                    setText(name.toString());
                }
            }
        });

        listView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue == null) {
                        return;
                    }

                    if (content.getChildren().isEmpty()) {
                        content.getChildren().setAll(bNode.node());
                    }
                    bNode.bind(newValue);
                }
        );
        if (nameSubPath != null) {
            listeners.add(oldName.append("*").append(nameSubPath.startsWith("translate:") ? nameSubPath.substring("translate:".length()) : nameSubPath), s -> {
                int i = listView.getSelectionModel().getSelectedIndex();
                listView.getItems().set(i, listView.getItems().get(i));
                listView.getSelectionModel().select(i);
            });
        }

        ScrollPane scroll = new ScrollPane(grow(content));
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        Button add = button("Add");
        Button remove = button("Remove", () -> {
        });

        Pane buttons = groupButtons(add, remove);
        Node views = fixedSize ? line(0, 0, listView, grow(scroll)) : line(0, 0, column(growV(listView), buttons), grow(scroll));

        BNode result = bind(views, s -> {
            RefStorage.List lst = (RefStorage.List) s;
            content.getChildren().clear();
            listView.getItems().setAll(lst);
            if (listView.getSelectionModel().getSelectedIndex() < 0) {
                listView.getSelectionModel().select(0);
            }
        });
        withAction(add, () -> {
            RefStorage struct = elementDefaultDeclaration.createDefault();
            result.asList().add(struct);
            listView.getItems().add(struct);
            listView.getSelectionModel().select(listView.getItems().size() - 1);
        });
        currentName.pop();
        return result;
    }

    private BNode listGenericUi(GenericType type, Declaration declaration) {
        return stub();
    }

    private BNode listUi(DefType elementType, Declaration declaration, boolean fixedSize) {
        return switch (elementType) {
            case StructData s -> listStructUi(declaration, fixedSize);
            case EnumData e -> listLineUi(declaration, fixedSize);
            case GenericType g -> listGenericUi(g, declaration);
            case StdType t -> listLineUi(declaration, fixedSize);
            default -> throw new IllegalStateException("Unexpected value: " + elementType);
        };
    }

    private BNode listLineUi(Declaration declaration, boolean fixedSize) {
        if (!fixedSize) {
            return stub();
        }
        List<BNode> nodes = new ArrayList<>();
        HBox line = line(0, 0);
        line.getStyleClass().add("array_line");
        int size = Integer.parseInt((String) declaration.asGeneric().params.get(1));
        for (int i = 0; i < size; i++) {
            currentName.push(currentName().append(i + ""));
            Declaration data = declaration.extractElement();

            NameTree meta = new NameTree();
            meta.value("group:collapsable", "false");
            meta.value("dependant", "false");
            String value = data.meta().value("name:children");
            if (value == null) {
                value = "{i}";
            }
            meta.value("name", value.replace("{i}", i + ""));
            data = data.withMeta(meta.withParent(data.meta()));

            BNode node = typeUi(data, true);
            Node n = declaration.meta().is("group:collapsable") ? node.node() : groupCollapsable(data, node.node(), false);
            if (i == 0) {
                n.getStyleClass().add("first");
            } else if (i == size - 1) {
                n.getStyleClass().add("last");
            }
            line.getChildren().add(n);
            nodes.add(node);
            currentName.pop();
        }
        BNode n = bind(line, s -> {
            RefStorage.List lst = (RefStorage.List) s;
            for (int i = 0; i < size; i++) {
                nodes.get(i).bind(lst.get(i));
            }
        });

        if (declaration.meta().is("group:collapsable")) {
            return n.replace(groupCollapsable(declaration, n.node(), true));
        } else {
            return n.replace(simpleGroup(declaration.textName(), n.node()));
        }
    }

    private BNode bitsetUi(Declaration declaration) {

        JFXChipView<KV> textField = withMeta(new JFXChipView<>() {
            @Override
            public Orientation getContentBias() {
                return Orientation.HORIZONTAL;
            }
        }, declaration.meta());

        textField.setSkin(new JFXChipViewSkin<>(textField));

        String v = declaration.meta().value("enum:fixed");
        List<KV> fieldValues = KV.parse(v);

        textField.getSuggestions().addAll(fieldValues);
        textField.setPredicate((value, s) -> value.toString().toLowerCase().startsWith(s.toLowerCase()) && !textField.getChips().contains(value));

        Pane node = (Pane) growH(withLabel(textField, declaration.textName()));
        node.setPrefHeight(32);
        return bind(node, refStorage -> {
            List<KV> r = new ArrayList<>();
            long parsed = Long.parseLong(refStorage.self(), 16);
            for (KV kv : fieldValues) {
                long bits = Long.parseLong(kv.value, 16);
                if ((bits & parsed) == bits) {
                    r.add(kv);
                }
            }
            textField.getChips().setAll(r);
        });
    }

    private BNode simpleUi(Declaration data, boolean hasExternalTitle) {
        return switch (data.asStd().stdType) {
            case STR -> stringUi(data);
            case _BOOL8_ -> boolUi(data);
            case _INT16_, _INT32_ -> intUi(data, hasExternalTitle);
            case TRANSLATED -> translatedUi(data);
            case TRANSLATED_TEXT -> translatedTextUi(data);
            case _FLOAT_ -> floatUi(data, hasExternalTitle);
            default -> stub();
        };
    }

    private BNode boolUi(Declaration data) {
        ComboBox<String> combo = withMeta(new ComboBox<>(), data.meta());
        combo.getItems().setAll("true", "false");
        combo.setPrefWidth(54);

        String value = data.defaultValue().isEmpty() ? "false" : data.defaultValue().toLowerCase();
        combo.setValue(value);

        return bind(withLabel(combo, data.textName()), s -> combo.setValue(s.self().toLowerCase()));
    }

    private BNode intUi(Declaration data, boolean hasExternalTitle) {
        String fixed = data.meta().value("enum:fixed");
        String dynamic = data.meta().value("enum:dynamic");
        if (fixed != null || dynamic != null) {
            ComboBox<KV> combo = withMeta(new SearchableComboBox<>(), data.meta());
            SearchableComboBoxSkin<KV> skin = new SearchableComboBoxSkin<>(combo) {

                private ComboBox<KV> cb;

                {
                    cb = (ComboBox<KV>) getChildren().get(0);
                }

                @Override
                protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
                    return cb.prefWidth(-1);
                }
            };
            combo.setSkin(skin);
            List<KV> fixedValues = fixed == null ? List.of() : KV.parse(fixed);

            Node n = hasExternalTitle ? combo : withLabel(combo, data.textName());

            BNode node = bind(n, s -> {
                List<KV> dynamicValues = dynamic == null ? List.of() : KV.parse(dynamic, storage, translations);
                List<KV> values = new ArrayList<>(fixedValues.size() + dynamicValues.size());
                values.addAll(fixedValues);
                values.addAll(dynamicValues);

                combo.getItems().setAll(values);
                combo.setValue(values.stream().filter(k -> k.value.equals(s.self())).findFirst().orElse(values.get(0)));
            });
            combo.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    set(node, newValue.value);
                }
            });
            return node;
        }
        int min = 0;
        int max = 100;

        String range = data.meta().value("range");
        if (range != null) {
            String[] parts = range.split(";");
            min = Integer.parseInt(parts[0]);
            max = Integer.parseInt(parts[1]);
        }
        int value = data.defaultValue().isEmpty() ? min : Integer.parseInt(data.defaultValue());

        Spinner<Integer> textField = withMeta(new Spinner<>(min, max, value), data.meta());
        textField.setMaxWidth(String.valueOf(max).length() * 6 + 28);
        textField.setEditable(true);
        return bind(withLabel(textField, data.textName()), s -> textField.getEditor().setText(s.self()));
    }

    private BNode enumUi(Declaration data) {
        ComboBox<KV> combo = withMeta(new SearchableComboBox<>(), data.meta());
        combo.setSkin(new SearchableComboBoxSkin<>(combo) {
            private ComboBox<KV> cb;

            {
                cb = (ComboBox<KV>) getChildren().get(0);
            }

            @Override
            protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
                return cb.prefWidth(-1);
            }
        });
        List<KV> values = new ArrayList<>();
        int i = 0;
        for (EnumValue value : data.asEnum().values) {
            values.add(new KV(value.toHuman(), String.valueOf(i++)));
        }

        combo.getItems().setAll(values);

        return bind(withLabel(combo, data.textName()), s -> {
            combo.setValue(values.stream().filter(k -> k.value.equals(s.self())).findFirst().orElse(values.get(0)));
        });
    }

    private BNode floatUi(Declaration data, boolean hasExternalTitle) {
        double min = 0;
        double max = 1;

        String range = data.meta().value("range");
        if (range != null) {
            String[] parts = range.split(";");
            min = Double.parseDouble(parts[0]);
            max = Double.parseDouble(parts[1]);
        }
        double value = data.defaultValue().isEmpty() ? min : Double.parseDouble(data.defaultValue());

        Spinner<Double> textField = withMeta(new Spinner<>(min, max, value, 0.01), data.meta());
        textField.setMaxWidth((max + "0").length() * 6 + 28);
        textField.setEditable(true);

        Node node = hasExternalTitle ? textField : withLabel(textField, data.textName());
        return bind(node, s -> textField.getValueFactory().setValue(Double.parseDouble(s.self())));
    }

    private BNode translatedUi(Declaration data) {
        TextField path = textField("");
        DynamicPath name = currentName();

        TextField translation = textField("");

        BNode node = bind(withLabel(column(0, path, translation), data.textName()), s -> {
            String txt = name.toStaticPath().substring("root.".length());
            path.setPromptText(txt);
            path.setText(s.self());
            String str = translations.get(path.getText().isBlank() ? txt : path.getText());
            translation.setText(str);
        });

        path.textProperty().addListener((observable, oldValue, newValue) -> {
            set(node, newValue);
        });
        return node;
    }

    private BNode translatedTextUi(Declaration data) {
        return translatedUi(data);
    }

    private BNode stringUi(Declaration data) {
        TextField field = withMeta(textField(data.defaultValue()), data.meta());
        BNode result = bind(withLabel(field, data.textName()), s -> field.setText(s.self()));
        field.textProperty().addListener((observable, oldValue, newValue) -> set(result, newValue));
        return result;
    }

    private void set(BNode node, String value) {
        if (value == null || node.data().self().equals(value)) {
            return;
        }
        node.data().self(value);
        node.bind(node.data());
        for (Consumer<String> consumer : listeners.get(node.name().toStaticPath())) {
            consumer.accept(value);
        }
    }

    private <T extends Region> T withMeta(T t, NameTree meta) {
        if (t instanceof TextField ti) {
            String sample = meta.value("sample");
            if (sample != null) {
                ti.setPrefColumnCount(sample.length());
            }
        }

        {
            String minWidth = meta.value("min_width");
            if (minWidth != null) {
                t.setMinWidth(Double.parseDouble(minWidth));
            }
        }

        return t;
    }

    private BNode stub() {
        String name = StackWalker.getInstance()
                .walk(f -> f.map(StackWalker.StackFrame::getMethodName)
                        .skip(1)
                        .findFirst()
                        .get());
        return bind(new Label("Not supported yet: " + name), s -> {
        });
    }

    private <T extends Node> BNode bind(T node, Consumer<RefStorage> onBind) {
        return new BNode(currentName(), node, onBind);
    }

    private BNode typeUi(Declaration data, boolean hasExternalTitle) {
        BNode result = switch (data.type()) {
            case StructData s -> structUi(data, hasExternalTitle);
            case EnumData e -> enumUi(data);
            case GenericType g -> genericUi(data);
            case StdType t -> simpleUi(data, hasExternalTitle);
            default -> throw new RuntimeException("Unknown type: " + data);
        };
        if (data.meta().is("dependant")) {
            DynamicPath current = currentName.pop();
            DynamicPath previous = currentName();
            currentName.push(current);

            String value = data.meta().value("dependant");
            String[] values = value.split("/");
            String name = values[0];
            Set<String> allowed = Set.of(values[1].split(","));
            DynamicPath path = previous.append(name);
            listeners.add(path, s -> {
                result.node().setVisible(allowed.contains(s));
                result.node().setManaged(allowed.contains(s));
            });
            String v = storage.value(path.toStaticPath());
            if (v != null) {
                result.node().setVisible(allowed.contains(v));
                result.node().setManaged(allowed.contains(v));
            }
        }
        return result;
    }

    private record KV(String name, String value) {
        @Override
        public String toString() {
            return name();
        }

        static List<KV> parse(String text) {
            String[] kv = text.substring(1, text.length() - 1).split("},\\{");
            List<KV> result = new ArrayList<>(kv.length);
            for (String t : kv) {
                int i = t.indexOf(':');
                result.add(new KV(t.substring(0, i), t.substring(i + 1)));
            }
            return result;
        }

        static List<KV> parse(String text, RefStorage storage, Map<String, String> translations) {
            String[] parts = text.split("/", 2);
            String pathToList = parts[0];
            String pathToName = parts[1];

            List<KV> result = new ArrayList<>();

            RefStorage child = storage.childForPath(pathToList);
            if (child == RefStorage.EMPTY) {
                return List.of(new KV("(Not available)", ""));
            }
            RefStorage.List refStorage = (RefStorage.List) child;
            int i = 0;
            for (RefStorage item : refStorage) {
                if (pathToName.startsWith("translate:")) {
                    String subnameAfterTranslate = pathToName.substring("translate:".length());
                    String path = item.childForPath(subnameAfterTranslate).self();
                    if (path == null || path.isEmpty()) {
                        path = pathToList + "." + i + "." + subnameAfterTranslate;
                    }
                    String str = translations.get(path);
                    result.add(new KV(str, i + ""));
                } else {
                    //RefStorage refStorage = item.childForPath(nameSubPath);
                    //name.append(refStorage.self());

                    //setText(name.toString());
                }
                i++;
            }

            return result;
        }
    }
}
