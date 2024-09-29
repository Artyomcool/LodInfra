package com.github.artyomcool.lodinfra.dateditor.ui;

import com.github.artyomcool.lodinfra.dateditor.grammar.RefStorage;
import javafx.scene.Node;

import java.util.function.Consumer;

public class BNode {

    private final DynamicPath name;
    private final Node node;
    private final Consumer<RefStorage> onBind;
    private RefStorage data;

    public BNode(DynamicPath name, Node node, Consumer<RefStorage> onBind) {
        this.name = name;
        this.node = node;
        this.onBind = onBind;
    }

    public BNode replace(Node node) {
        return new BNode(name, node, onBind);
    }

    public void bind(RefStorage data) {
        this.data = data;
        onBind.accept(data);
    }

    public RefStorage data() {
        return data;
    }

    public DynamicPath name() {
        return name;
    }

    public RefStorage.Struct asStruct() {
        return (RefStorage.Struct) data;
    }

    public RefStorage.List asList() {
        return (RefStorage.List) data;
    }

    public Node node() {
        return node;
    }

}
