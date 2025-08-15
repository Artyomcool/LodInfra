package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.data.Helpers;
import com.github.artyomcool.lodinfra.data.dto.DataEntry;
import com.github.artyomcool.lodinfra.data.dto.Field;
import com.github.artyomcool.lodinfra.data.dto.Config;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.StackPane;

import java.util.*;
import java.util.function.Consumer;

public class Context {
    final Config config;
    final Map<String, List<StringProperty>> properties = new HashMap<>();
    final Map<String, ObservableValue<?>> refs = new HashMap<>();
    final List<DelayedNode> delayedNodes = new ArrayList<>();

    final Deque<Map<String, ?>> vars = new ArrayDeque<>();
    final Deque<String> path = new ArrayDeque<>();
    final Deque<Runnable> undo = new ArrayDeque<>();
    final Map<String, List<Consumer<Boolean>>> dirtyListeners = new HashMap<>();

    final DataEntry data;

    boolean inNotifyProcess = false;

    Context(Config config, Map<String, ?> toSerialize, DataEntry localData) {
        this.config = config;
        vars.push(config.enums);
        vars.push(config.dynamicEnums(toSerialize));
        data = localData;
    }

    public String path() {
        String path = String.join("/", (Iterable<String>) this.path::descendingIterator);
        if (path.contains("${")) {
            Map<String, Object> params = new HashMap<>();
            for (Map<String, ?> var : vars) {
                params.putAll(var);
            }
            path = Helpers.evalString(path, params);
        }
        if (path.contains("/../")) {
            List<String> parts = new ArrayList<>(Arrays.asList(path.split("/")));
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).equals("..")) {
                    parts.remove(--i);
                    parts.remove(i--);
                }
            }
            path = String.join("/", parts);
        }

        return path;
    }

    public Object get(String path) {
        return get(path.split("/"));
    }

    Object get(String[] path) {
        Object d = data;
        for (int i = 0; i < path.length; ) {
            String tp = path[i++];
            if ("".equals(tp)) {
                continue;
            }
            d = ((Map) d).get(tp);
            if (i == path.length) {
                break;
            }
            if (d instanceof List) {
                String p = path[i++];
                switch (p) {
                    case "i64": {
                        int t = Integer.parseInt(path[i++]);

                        long v2 = 0xffffffffL & Helpers.intFromList((List<Integer>) d, t);
                        long v1 = 0xffffffffL & Helpers.intFromList((List<Integer>) d, t + 4);

                        d = v1 << 32 | v2;
                        break;
                    }
                    case "i32": {
                        int t = Integer.parseInt(path[i++]);
                        d = Helpers.intFromList((List<Integer>) d, t);
                        break;
                    }
                    case "i16": {
                        List<Integer> lst = (List<Integer>) d;
                        int t = Integer.parseInt(path[i++]);

                        int v2 = lst.get(t++);
                        int v1 = lst.get(t++);

                        d = v1 << 8 | v2;
                        break;
                    }
                    case "i8":
                        d = ((List<?>) d).get(Integer.parseInt(path[i++]));
                        break;
                    default: {
                        String[] split = p.split("\\?", -1);
                        p = split[0];
                        int t = Integer.parseInt(p);
                        List<?> lst = (List<?>) d;
                        d = t >= lst.size() ? (split.length > 1 ? split[1] : null) : lst.get(t);
                        break;
                    }
                }
            }
        }
        return d;
    }

    private void set(String[] path, Object value, boolean notify, boolean undo) {
        config.clearDynamicEnumCache();
        Object d = data;
        for (int i = 0; i < path.length - 1; i++) {
            if (d instanceof Map) {
                d = ((Map<?, ?>) d).get(path[i]);
            } else if (d instanceof List) {
                String p = path[i];
                String[] split = p.split("\\?", -1);
                p = split[0];
                int t = Integer.parseInt(p);
                List<?> lst = (List<?>) d;
                d = lst.get(t);
            }
        }
        boolean changed = false;
        if (d instanceof Map) {
            Object old = value == null ? ((Map) d).remove(path[path.length - 1]) : ((Map) d).put(path[path.length - 1], value);
            if (!Objects.equals(Helpers.string(old), Helpers.string(value))) {
                pushUndo(path, old, undo);
                changed = true;
            }
        } else if (d instanceof List) {
            String p = path[path.length - 1];
            String[] split = p.split("\\?", -1);
            p = split[0];
            int t = Integer.parseInt(p);
            List lst = (List) d;
            boolean trimList = lst.size() <= t;
            if (trimList) {
                pushUndoTrim(path, lst.size(), undo);
            }
            while (lst.size() <= t) {
                lst.add(null);
            }
            if (!trimList) {
                Object old = lst.get(t);
                if (!Objects.equals(Helpers.string(old), Helpers.string(value))) {
                    pushUndo(path, old, undo);
                    changed = true;
                }
            }
            lst.set(t, value);
        }

        String key = String.join("/", path);
        if (!undo) {
            if (changed) {
                markDirty(key);
            }
        }
        if (notify && changed) {
            inNotifyProcess = true;
            for (StringProperty property : properties.getOrDefault(key, Collections.emptyList())) {
                property.setValue(Helpers.string(value));
            }
            inNotifyProcess = false;
        }
    }

    void set(String[] path, Object value) {
        set(path, value, false, false);
    }

    private void pushUndo(String[] path, Object old, boolean undo) {
        if (!undo) {
            this.undo.push(() -> set(path, old, true, !undo));
        }
    }

    private void pushUndoTrim(String[] path, int size, boolean undo) {
        if (!undo) {
            this.undo.push(() -> set(path, trim(path, size), true, !undo));
        }
    }

    private List<?> trim(String[] path, int size) {
        List<?> lst = (List<?>) get(path);
        while (lst.size() > size) {
            lst.remove(size);
        }
        return lst;
    }

    void set(Object value) {
        set(this.path().split("/"), value);
    }

    public Object currentValue() {
        String key = this.path();
        String[] path = key.split("/");
        return get(path);
    }

    public Property<Object> createRawProperty() {  // FIXME
        String key = this.path();
        String[] path = key.split("/");

        Property<Object> result = new SimpleObjectProperty<>();
        result.setValue(get(path));
        listen(path, result);
        return result;
    }

    private void listen(String[] path, Property<?> result) {
        result.addListener((observable, oldValue, newValue) -> {
            if (!inNotifyProcess) {
                set(path, newValue);
            }
        });
    }

    public void addProperty(StringProperty property) {
        String key = this.path();
        String[] path = key.split("/");
        properties.computeIfAbsent(key, k -> new ArrayList<>()).add(property);
        property.setValue(Helpers.string(get(path)));
        listen(path, property);
    }

    public void markDirty(String path) {
        if (data.dirty.add(path)) {
            ArrayDeque<String> pathDeque = new ArrayDeque<>(Arrays.asList(path.split("/")));
            while (true) {
                for (Consumer<Boolean> consumer : dirtyListeners.getOrDefault(String.join("/", pathDeque), Collections.emptyList())) {
                    consumer.accept(true);
                }
                if (pathDeque.isEmpty()) {
                    break;
                }
                pathDeque.removeLast();
            }
        }
    }

    private Object evaluate(Object data, Map<String, Object> params) {
        if (data instanceof String) {
            return Helpers.evalString((String) data, params);
        }
        if (data instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) data;
            Map<String, Object> res = new HashMap<>(m.size());
            for (Map.Entry<String, Object> e : m.entrySet()) {
                res.put(Helpers.evalString(e.getKey(), params), evaluate(e.getValue(), params));
            }
            return res;
        }
        if (data instanceof List) {
            List<?> l = (List<?>) data;
            List<Object> res = new ArrayList<>(l.size());
            for (Object o : l) {
                res.add(evaluate(o, params));
            }
        }
        return data;
    }

    public void push(Field field) {
        push(field.id, field.vars);
    }

    public void push(String id, Map<String, Object> vars) {
        if (id != null && !"".equals(id)) {
            path.push(id);
        }
        if (vars != null) {
            this.vars.push((Map<String, Object>) evaluate(vars, params()));
        }
    }

    public void pop() {
        path.pop();
        vars.pop();
    }

    public void pop(Field field) {
        if (field.id != null && !"".equals(field.id)) {
            path.pop();
        }
        if (field.vars != null) {
            vars.pop();
        }
    }

    public Map<String, Object> params() {
        Map<String, Object> params = new HashMap<>();
        vars.descendingIterator().forEachRemaining(params::putAll);
        return params;
    }

    public Map<String, Field> structs() {
        return config.structs;
    }

    public State state() {
        return new State(vars, path);
    }

    public void delay(StackPane node, Field field) {
        delayedNodes.add(new DelayedNode(node, field, vars, path));
    }

    public void ref(String id, ObservableValue<?> observable) {
        if (id != null) {
            refs.put(id, observable);
        }
    }

    public void registerDirty(Consumer<Boolean> listener) {
        String path = path();
        dirtyListeners.computeIfAbsent(path, k -> new ArrayList<>()).add(listener);
        if (data.dirty.contains(path)) {
            listener.accept(true);
            return;
        }

        path += "/";

        for (String dirty : data.dirty) {
            if (dirty.startsWith(path)) {
                listener.accept(true);
                return;
            }
        }

        listener.accept(false);
    }

    public void cleanUpDirty() {
        for (List<Consumer<Boolean>> value : dirtyListeners.values()) {
            for (Consumer<Boolean> consumer : value) {
                consumer.accept(false);
            }
        }
    }

    public void undo() {
        Runnable u = undo.poll();
        if (u != null) {
            u.run();
        }
    }

    public void apply(String path, Object element) {
        applyInternal(path, element);
    }

    private void applyInternal(String path, Object element) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (element instanceof List) {
            List newList = (List) element;
            List oldList = (List) get(path);
            if (newList.size() < oldList.size()) {
                // FIXME
                throw new UnsupportedOperationException("Can't reduce size of list " + path);
            } else {
                for (int i = 0; i < newList.size(); i++) {
                    applyInternal(path + "/" + i, newList.get(i));
                    // TODO add?
                }
            }
        } else if (element instanceof Map) {
            Map<?,?> newMap = (Map) element;
            Map oldMap = (Map) get(path);

            Set<String> keys = new HashSet<>(oldMap.keySet());
            for (Map.Entry<?,?> entry : newMap.entrySet()) {
                if (!keys.remove(entry.getKey())) {
                    // TODO add
                }
                applyInternal(path + "/" + entry.getKey(), entry.getValue());
            }
        } else {
            set(path.split("/"), element, true, false);
        }
    }

    class State {

        final Deque<Map<String, ?>> vars;
        final Deque<String> path;

        State(Deque<Map<String, ?>> vars, Deque<String> path) {
            this.vars = new ArrayDeque<>(vars);
            this.path = new ArrayDeque<>(path);
        }

        State swap() {
            State tmp = new State(Context.this.vars, Context.this.path);
            restore();
            return tmp;
        }

        void restore() {
            Context.this.vars.clear();
            Context.this.vars.addAll(vars);
            Context.this.path.clear();
            Context.this.path.addAll(path);
        }

    }

    class DelayedNode extends State {
        final StackPane parent;
        final Field field;

        DelayedNode(StackPane parent, Field field, Deque<Map<String, ?>> vars, Deque<String> path) {
            super(vars, path);
            this.parent = parent;
            this.field = field;
        }
    }
}
