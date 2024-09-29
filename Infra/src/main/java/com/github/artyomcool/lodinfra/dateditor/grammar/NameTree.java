package com.github.artyomcool.lodinfra.dateditor.grammar;

import java.util.TreeMap;

public class NameTree extends TreeMap<String, NameTree> implements RefStorage {
    public NameTree parent;
    private String self;

    @Override
    public String value(String path) {
        String value = RefStorage.super.value(path);
        return value == null && parent != null ? parent.value(path) : value;
    }

    @Override
    public String self() {
        return self;
    }

    @Override
    public void self(String value) {
        this.self = value;
    }

    @Override
    public RefStorage child(String name) {
        NameTree nameTree = get(name);
        if (nameTree == null) {
            return RefStorage.empty();
        }
        return nameTree;
    }

    public void value(String path, String value) {
        if (path.isEmpty()) {
            self(value);
            return;
        }

        int index = path.indexOf('.');
        if (index == -1) {
            computeIfAbsent(path, k -> new NameTree()).self(value);
            return;
        }

        NameTree refStorage = computeIfAbsent(path.substring(0, index), k -> new NameTree());
        refStorage.value(path.substring(index + 1), value);
    }

    public boolean is(String name) {
        String value = value(name);
        return value != null && !value.equals("false");
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (self() != null) {
            result.append('=').append(self()).append(';');
        } else if (isEmpty()) {
            result.append(';');
        } else {
            for (var entry : entrySet()) {
                result.append(entry.getKey()).append(".").append(entry.getValue());
            }
        }
        return result.toString();
    }

    public NameTree withParent(NameTree meta) {
        NameTree tree = new NameTree();
        tree.parent = meta;
        tree.putAll(this);
        return tree;
    }
}
