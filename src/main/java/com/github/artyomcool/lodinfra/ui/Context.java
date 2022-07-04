package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.data.Helpers;
import com.github.artyomcool.lodinfra.data.dto.Field;
import com.github.artyomcool.lodinfra.data.dto.Config;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.StackPane;

import java.util.*;

public class Context {
    final Config config;
    final Map<String, List<StringProperty>> properties = new HashMap<>();
    final Map<String, ObservableValue<?>> refs = new HashMap<>();
    final List<DelayedNode> delayedNodes = new ArrayList<>();

    final Deque<Map<String, ?>> vars = new ArrayDeque<>();
    final Deque<String> path = new ArrayDeque<>();

    final Map<String, Object> data;

    Context(Config config, Map<String, ?> toSerialize, Map<String, Object> localData) {
        this.config = config;
        vars.push(config.enums);
        vars.push(config.dynamicEnums(toSerialize));
        data = localData;
    }

    private String path() {
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

    Object get(Map<String, Object> data, String[] path) {
        Object d = data;
        for (int i = 0; i < path.length; ) {
            d = ((Map) d).get(path[i++]);
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

    void set(Map<String, Object> data, String[] path, Object value) {
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
                break;
            }
        }
        if (d instanceof Map) {
            ((Map) d).put(path[path.length - 1], value);
        } else if (d instanceof List) {
            String p = path[path.length - 1];
            String[] split = p.split("\\?", -1);
            p = split[0];
            int t = Integer.parseInt(p);
            List lst = (List) d;
            while (lst.size() <= t) {
                lst.add(null);
            }
            lst.set(t, value);
        }
    }

    void set(Object value) {
        set(data, this.path().split("/"), value);
    }

    public Object currentValue() {
        String key = this.path();
        String[] path = key.split("/");
        return get(data, path);
    }

    public Property<Object> currentProperty() {
        String key = this.path();
        String[] path = key.split("/");

        Property<Object> result = new SimpleObjectProperty<>();
        result.setValue(get(data, path));
        result.addListener((observable, oldValue, newValue) -> set(data, path, newValue));
        return result;
    }

    public void addProperty(StringProperty property) {
        String key = this.path();
        String[] path = key.split("/");
        properties.computeIfAbsent(key, k -> new ArrayList<>()).add(property);
        property.setValue(Helpers.string(get(data, path)));
        property.addListener((observable, oldValue, newValue) -> {
            config.clearDynamicEnumCache();
            Object t = data;
            for (int i = 0; i < path.length - 1; i++) {
                if (t instanceof List) {
                    t = ((List) t).get(Integer.parseInt(path[i]));
                } else {
                    t = ((Map) t).get(path[i]);
                }
            }
            String n = path[path.length - 1];
            if (t instanceof List) {

                String[] split = n.split("\\?", -1);
                n = split[0];

                ((List) t).set(Integer.parseInt(n), newValue);
            } else {
                ((Map) t).put(n, newValue);
            }
        });
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
        if (id != null) {
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
        if (field.id != null) {
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
