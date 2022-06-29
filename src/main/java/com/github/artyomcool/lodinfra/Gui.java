package com.github.artyomcool.lodinfra;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.jfoenix.controls.JFXChipView;
import impl.org.controlsfx.skin.SearchableComboBoxSkin;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.controlsfx.control.textfield.CustomTextField;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Gui extends Application {

    private static final Map<String, Function<List<Object>, Object>> stdFunctions = new HashMap<>();

    static {
        stdFunctions.put("size", objects -> {
            if (objects.size() != 1) {
                throw new IllegalArgumentException("Not supported: " + objects);
            }
            Object obj = objects.get(0);
            if (obj instanceof String) {
                return (long) ((String) obj).length();
            }
            if (obj instanceof List) {
                return (long) ((List<?>) obj).size();
            }
            return (long) ((Map) obj).size();
        });
        stdFunctions.put("get", objects -> {
            if (objects.size() != 2) {
                throw new IllegalArgumentException("Not supported: " + objects);
            }
            Object obj = objects.get(0);
            Object path = objects.get(1);
            if (obj instanceof List) {
                return ((List<?>) obj).get(Integer.parseInt(string(path)));
            }
            return ((Map) obj).get(string(path));
        });
        stdFunctions.put("regex", objects -> {
            String d = (String) objects.get(0);
            Pattern pattern = Pattern.compile((String) objects.get(1));
            Matcher matcher = pattern.matcher(d);
            if (!matcher.matches()) {
                return null;
            }
            return matcher.group(((Number) objects.get(2)).intValue());
        });
        stdFunctions.put("num", objects -> Long.parseLong(string(objects.get(0))));
    }

    private static final Gson gson = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setPrettyPrinting()
            .create();

    private static class Ui {
        List<TabGroup> tabs;
        Map<String, Field> structs;
        Map<String, List<Value>> enums;
        Map<String, DynamicEnum> dynamicEnums;
        Map<String, Format> formats;
    }

    private static class Format {
        String template;
        Map<String, ProcessorWrapper> processors;
        Map<String, String> structs;
        Map<String, Alias> aliases;
    }

    private static class ReadWrapper {
        final String data;
        int pos;

        ReadWrapper(String data) {
            this.data = data;
        }
    }

    private static class ProcessorWrapper {
        Processor read;
        WriteProcessor write;

        List<String> read(ReadWrapper reader) {

            Deque<Processor> processors = new ArrayDeque<>();
            processors.push(read);

            List<String> result = new ArrayList<>();
            StringBuilder next = new StringBuilder();
            int[] len = new int[]{0};

            Map<String, Object> params = new HashMap<>();
            params.put("self", (Supplier<String>) () -> reader.data.substring(reader.pos, reader.pos + len[0]));

            Map<String, Function<List<Object>, Object>> functions = new HashMap<>();
            functions.put("any", objects -> {
                if (objects.isEmpty()) {
                    len[0] = 1;
                    return true;
                }
                for (Object ch : objects) {
                    if (reader.data.startsWith((String) ch, reader.pos)) {
                        len[0] = ((String) ch).length();
                        return true;
                    }
                }
                return false;
            });
            functions.put("append", objects -> {
                for (Object ch : objects) {
                    next.append(ch);
                }
                return null;
            });
            functions.put("nextElement", objects -> {
                if (next.length() > 0) {
                    result.add(next.toString());
                    next.setLength(0);
                }
                return null;
            });
            functions.put("pop", objects -> {
                processors.pop();
                return null;
            });
            functions.put("popAndRollback", objects -> {
                processors.pop();
                len[0] = 0;
                return null;
            });

            while (reader.pos < reader.data.length()) {
                for (Map.Entry<String, Term> entry : processors.peek().entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("$")) {
                        key = key.substring(1);
                        if ((boolean) Interpreter.eval(key, params, functions)) {
                            Term term = entry.getValue();
                            if (term.action != null) {
                                Interpreter.eval(term.action, params, functions);
                            }
                            if (term.push != null) {
                                processors.push(term.push);
                            }
                            reader.pos += len[0];
                            break;
                        }
                        continue;
                    }

                    if (reader.data.startsWith(key, reader.pos)) {
                        len[0] = key.length();
                        Term term = entry.getValue();
                        if (term.action != null) {
                            Interpreter.eval(term.action, params, functions);
                        }
                        if (term.push != null) {
                            processors.push(term.push);
                        }
                        reader.pos += len[0];
                        break;
                    }
                }
            }

            if (next.length() > 0) {
                result.add(next.toString());
            }
            return result;
        }

        String write(List<?> objs) {
            StringBuilder writer = new StringBuilder();
            int[] len = new int[]{0};

            for (Object obj : objs) {
                ReadWrapper text = new ReadWrapper(string(obj));

                Map<String, Object> params = new HashMap<>();
                params.put("self", (Supplier<String>) () -> text.data.substring(text.pos, text.pos + len[0]));

                Map<String, Function<List<Object>, Object>> functions = new HashMap<>();
                functions.put("any", objects -> {
                    if (objects.isEmpty()) {
                        len[0] = 1;
                        return true;
                    }
                    for (Object ch : objects) {
                        if (text.data.startsWith((String) ch, text.pos)) {
                            len[0] = ((String) ch).length();
                            return true;
                        }
                    }
                    return false;
                });
                functions.put("always", objects -> true);
                functions.put("contains", objects -> {
                    for (Object object : objects) {
                        if (text.data.contains(string(object))) {
                            return true;
                        }
                    }
                    return false;
                });
                functions.put("append", objects -> {
                    for (Object ch : objects) {
                        writer.append(ch);
                    }
                    return null;
                });

                for (Map.Entry<String, String> entry : write.before.entrySet()) {
                    String condition = entry.getKey();
                    String action = entry.getValue();
                    if ((boolean) Interpreter.eval(condition, params, functions)) {
                        Interpreter.eval(action, params, functions);
                    }
                }

                Deque<Processor> processors = new ArrayDeque<>();
                processors.push(write.process);
                Processor processor = processors.peek();

                functions.put("pop", objects -> {
                    processors.pop();
                    return null;
                });
                functions.put("popAndRollback", objects -> {
                    processors.pop();
                    len[0] = 0;
                    return null;
                });

                while (text.pos < text.data.length()) {
                    for (Map.Entry<String, Term> entry : processor.entrySet()) {
                        String key = entry.getKey();
                        if (key.startsWith("$")) {
                            key = key.substring(1);
                            if (!((boolean) Interpreter.eval(key, params, functions))) {
                                continue;
                            }
                        } else if (text.data.startsWith(key, text.pos)) {
                            len[0] = key.length();
                        } else {
                            continue;
                        }

                        Term term = entry.getValue();
                        if (term.action != null) {
                            Interpreter.eval(term.action, params, functions);
                        }
                        if (term.push != null) {
                            processors.push(term.push);
                        }
                        text.pos += len[0];
                        break;
                    }
                }

                for (Map.Entry<String, String> entry : write.after.entrySet()) {
                    String condition = entry.getKey();
                    String action = entry.getValue();
                    if ((boolean) Interpreter.eval(condition, params, functions)) {
                        Interpreter.eval(action, params, functions);
                    }
                }
            }

            return writer.toString();
        }
    }

    private static class Processor extends LinkedHashMap<String, Term> {

    }

    private static class WriteProcessor {
        Processor process;
        Map<String, String> before = new LinkedHashMap<>();
        Map<String, String> after = new LinkedHashMap<>();
    }

    private static class Term {
        String action;
        Processor push;
    }

    private static class DynamicEnum {
        String fromTab;
        Value template;
    }

    private static class Alias {

        final transient IdentityHashMap<Object, Object> parsedCache = new IdentityHashMap<>();
        transient Map<String, ProcessorWrapper> processors;

        String path;
        Map<String, Filter> filters;
        Map<String, Map<String, Object>> structs;

        @SuppressWarnings("rawtypes")
        private Object read(Object parsed, Map<String, ?> params, String path, int arraySize, String fullPath) {
            if (path == null) {
                return parsed;
            }
            if (path.startsWith("#")) {
                path = path.substring(1);
                // TODO extra params
                if (path.endsWith("]")) {
                    Matcher matcher = ARRAY.matcher(path);
                    if (!matcher.find()) {
                        throw new IllegalArgumentException("Wrong field name: " + path);
                    }

                    path = matcher.group(1);
                    String struct = matcher.group(2);
                    String[] substruct = struct.split(":");
                    struct = substruct[0];
                    Filter filter = substruct.length > 1 ? filters.get(substruct[1]) : null;

                    if (!path.isEmpty()) {
                        parsed = read(parsed, params, path, 0, fullPath + "/" + path);
                    }
                    List lst = (List) parsed;
                    Map<String, Object> p = new HashMap<>(params);
                    List<Object> r = new ArrayList<>(lst.size());
                    int j = 0;
                    for (int i = 0; i < lst.size(); i++) {
                        Object obj = lst.get(i);
                        if (filter != null) {
                            if (obj == null) {
                                continue;
                            }
                            Object t = obj;
                            for (String s : filter.field.split("/")) {
                                if (t instanceof List) {
                                    t = ((List) t).get(Integer.parseInt(s));
                                } else {
                                    t = ((Map) t).get(s);
                                }
                            }
                            if (!string(t).matches(filter.regex)) {
                                continue;
                            }
                            lst.set(i, null);
                        }
                        p.put("i", (long) i);
                        p.put("j", (long) j++);
                        r.add(read(obj, p, structs.get(struct), fullPath + "/" + path + "/" + i));
                    }
                    return r;
                }
                if (arraySize > 0) {
                    Map<String, Object> p = new HashMap<>(params);
                    List<Object> r = new ArrayList<>(arraySize == Integer.MAX_VALUE ? 16 : arraySize);
                    for (int i = 0; i < arraySize; i++) {
                        p.put("i", (long) i);
                        Map<String, Object> read = read(parsed, p, structs.get(path), fullPath + "/" + path + "/" + i);
                        if (arraySize == Integer.MAX_VALUE && read == null) {
                            break;
                        }
                        r.add(read);
                    }
                    return r;
                }
                return read(parsed, params, structs.get(path), fullPath + "/" + path);
            }
            if (arraySize > 0) {
                Map<String, Object> p = new HashMap<>(params);
                List<Object> r = new ArrayList<>(arraySize == Integer.MAX_VALUE ? 16 : arraySize);
                for (int i = 0; i < arraySize; i++) {
                    p.put("i", (long) i);
                    Object read = read(parsed, p, path, 0, fullPath + "/" + path + "/" + i);
                    if (arraySize == Integer.MAX_VALUE && read == null) {
                        break;
                    }
                    r.add(read);
                }
                return r;
            }
            path = evalString(path, params, null);
            String[] pathParts = path.split(":");
            path = pathParts[0];

            String[] p = path.split("/");
            for (int i = 0; i < p.length; i++) {
                if (parsed instanceof List) {
                    switch (p[i]) {
                        case "i64": {
                            String[] withDefault = p[++i].split("\\?");
                            int t = Integer.parseInt(withDefault[0]);
                            if (((List<?>) parsed).size() <= t) {
                                parsed = withDefault.length == 1 ? null : withDefault[1];
                                continue;
                            }

                            long v2 = 0xffffffffL & intFromList((List<Integer>) parsed, t);
                            long v1 = 0xffffffffL & intFromList((List<Integer>) parsed, t + 4);

                            parsed = String.valueOf(v1 << 32 | v2);
                        }
                        continue;
                        case "i32": {
                            String[] withDefault = p[++i].split("\\?");
                            int t = Integer.parseInt(withDefault[0]);
                            if (((List<?>) parsed).size() <= t) {
                                parsed = withDefault.length == 1 ? null : withDefault[1];
                                continue;
                            }
                            parsed = String.valueOf(intFromList((List<Integer>) parsed, t));
                        }
                        continue;
                        case "i16": {
                            String[] withDefault = p[++i].split("\\?");
                            int t = Integer.parseInt(withDefault[0]);
                            if (((List<?>) parsed).size() <= t) {
                                parsed = withDefault.length == 1 ? null : withDefault[1];
                                continue;
                            }

                            int v2 = (int) ((List<?>) parsed).get(t++);
                            int v1 = (int) ((List<?>) parsed).get(t++);

                            parsed = String.valueOf(v1 << 8 | v2);
                        }
                        continue;
                        case "i8":
                            String[] withDefault = p[++i].split("\\?");
                            int t = Integer.parseInt(withDefault[0]);
                            if (((List<?>) parsed).size() <= t) {
                                parsed = withDefault.length == 1 ? null : withDefault[1];
                                continue;
                            }
                            parsed = String.valueOf(((List<?>) parsed).get(t));
                            continue;
                    }
                }

                String[] withDefault = p[i].split("\\?");
                String[] withProcessor = withDefault[0].split("@");
                Object def = withDefault.length > 1 ? withDefault[1] : null;
                String fp = fullPath + "/" + path;
                if (parsed instanceof List || parsed instanceof String) {
                    int index = Integer.parseInt(withProcessor[0]);
                    List<?> lst = (List<?>) parsed;
                    parsed = index >= lst.size() ? def : lst.get(index);
                } else {
                    parsed = parsed == null ? def : ((Map) parsed).getOrDefault(p[i], def);
                }

                if (withProcessor.length > 1) {
                    String str = string(parsed);
                    parsed = parsedCache.computeIfAbsent(parsed, k -> processors.get(withProcessor[1]).read(new ReadWrapper(str)));
                }

            }
            if (pathParts.length > 1) {
                switch (pathParts[1]) {
                    case "str":
                        parsed = string(parsed);
                        break;
                    case "bit":
                        String s = BitSet.valueOf(new long[]{Long.parseLong(string(parsed).trim())}).toString();
                        parsed = "bits:" + s.substring(1, s.length() - 1);
                        break;
                }
            }
            return parsed;
        }

        private Map<String, Object> read(Object parsed, Map<String, ?> params, Map<String, ?> fields, String fullPath) {
            Map<String, Object> result = new HashMap<>();
            Object vars = fields.get("#vars");
            if (vars != null) {

                Map nParams = new HashMap<>(params);
                for (Map.Entry<String, String> entry : ((Map<String, String>) vars).entrySet()) {
                    nParams.put(evalString(entry.getKey(), params, stdFunctions), evalString(entry.getValue(), params, stdFunctions));
                }
                params = nParams;
            }

            String stop = (String) fields.get("#stopon");

            for (Map.Entry<String, ?> entry : fields.entrySet()) {
                String name = evalString(entry.getKey(), params, stdFunctions);
                if (name.startsWith("#")) {
                    continue;
                }

                String path = (String) entry.getValue();

                Matcher matcher = ARRAY.matcher(name);
                Object value;
                if (matcher.find()) {
                    name = matcher.group(1);
                    String count = matcher.group(2);
                    int c = count.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(count);
                    value = read(parsed, params, path, c, fullPath);
                } else {
                    value = read(parsed, params, path, 0, fullPath);
                }
                if (name.equals(stop) && value == null) {
                    return null;
                }
                result.put(name, value);
            }

            return result;
        }

        public Object read(Object parsed, Ui cfg) {
            if (structs.containsKey("#debug")) {
                System.out.println("Debug read: " + path);
            }
            return read(parsed, cfg.enums, this.path, 0, "");
        }

        private interface Put {
            void set(Object obj);

            void append(List<?> array);
        }

        private void ensureSize(List s, int size) {
            while (s.size() < size) {
                s.add(null);
            }
        }

        private Put forListNum(List serialized, String type, int offset) {
            return new Put() {
                @Override
                public void set(Object obj) {
                    switch (type) {
                        case "i64": {
                            long t = numberFromObj(obj);

                            int v8 = (int) (t & 0xff);
                            int v7 = (int) (t >>> 8 & 0xff);
                            int v6 = (int) (t >>> 16 & 0xff);
                            int v5 = (int) (t >>> 24 & 0xff);
                            int v4 = (int) (t >>> 32 & 0xff);
                            int v3 = (int) (t >>> 40 & 0xff);
                            int v2 = (int) (t >>> 48 & 0xff);
                            int v1 = (int) (t >>> 56 & 0xff);

                            ensureSize(serialized, offset + 8);
                            int j = offset;

                            serialized.set(j++, v8);
                            serialized.set(j++, v7);
                            serialized.set(j++, v6);
                            serialized.set(j++, v5);
                            serialized.set(j++, v4);
                            serialized.set(j++, v3);
                            serialized.set(j++, v2);
                            serialized.set(j++, v1);
                            break;
                        }
                        case "i32": {
                            int t = Math.toIntExact(numberFromObj(obj));

                            int v4 = t & 0xff;
                            int v3 = t >>> 8 & 0xff;
                            int v2 = t >>> 16 & 0xff;
                            int v1 = t >>> 24;

                            ensureSize(serialized, offset + 4);
                            int j = offset;

                            serialized.set(j++, v4);
                            serialized.set(j++, v3);
                            serialized.set(j++, v2);
                            serialized.set(j++, v1);
                            break;
                        }
                        case "i16": {
                            int t = Integer.parseInt(String.valueOf(numberFromObj(obj)));
                            ensureSize(serialized, offset + 2);
                            int j = offset;

                            int v2 = t & 0xff;
                            int v1 = t >>> 8 & 0xff;
                            serialized.set(j++, v2);
                            serialized.set(j++, v1);
                            break;
                        }
                        case "i8":
                            int v1 = Integer.parseInt(String.valueOf(numberFromObj(obj)));
                            ensureSize(serialized, offset + 1);
                            serialized.set(offset, v1);
                            break;
                        default:
                            throw new IllegalArgumentException(type + " is unknown type");
                    }
                }

                @Override
                public void append(List<?> array) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        long numberFromObj(Object obj) {
            String str = String.valueOf(obj);
            if (str.startsWith("bits:")) {
                str = str.substring("bits:".length());
                if (str.isEmpty()) {
                    return 0;
                }
                String[] split = str.split(", ");
                long r = 0;
                for (String s : split) {
                    r |= 1L << Integer.parseInt(s);
                }
                return r;
            }
            return Long.parseLong(str);
        }

        private void append(List<?> array, int offset, List serialized) {
            List dst;
            while (offset >= serialized.size()) {
                serialized.add(null);
            }

            if (serialized.get(offset) == null) {
                serialized.set(offset, dst = new ArrayList<>());
            } else {
                dst = (List) serialized.get(offset);
            }
            dst.addAll(array);
        }

        private Put forPut(String path, Map<String, Object> params, Object serialized, String fullPath) {
            String[] pathWithType = evalString(path, params, null).split(":");
            path = pathWithType[0];
            String type = pathWithType.length > 1 ? pathWithType[pathWithType.length - 1] : null;
            String[] pathParts = path.split("/");

            for (int i = 0; i < pathParts.length - 1; i++) {
                String t = pathParts[i];
                String[] withProcessor = t.split("@");
                if (serialized instanceof List) {
                    switch (t) {
                        case "i64":
                        case "i32":
                        case "i16":
                        case "i8":
                            return forListNum((List) serialized, t, Integer.parseInt(pathParts[++i].split("\\?")[0]));
                    }

                    int index = Integer.parseInt(withProcessor[0]);

                    ensureSize((List<?>) serialized, index + 1);
                    Object d = ((List<?>) serialized).get(index);
                    if (d == null) {
                        ((List) serialized).set(index, d = createNode(pathParts, i));
                    }
                    serialized = d;
                } else {
                    int pos = i;

                    serialized = ((Map) serialized).computeIfAbsent(withProcessor[0], k -> createNode(pathParts, pos));
                }
                if (serialized instanceof FormatParser.DataWrapper) {
                    serialized = ((FormatParser.DataWrapper) serialized).data;
                }
            }

            Object s = serialized;
            String lastPart = pathParts[pathParts.length - 1].split("@")[0];

            return new Put() {

                @Override
                public void set(Object obj) {
                    if ("bit".equals(type)) {
                        obj = numberFromObj(obj);
                    }
                    if (s instanceof List) {
                        int j = Integer.parseInt(lastPart.split("\\?")[0]);
                        ensureSize((List) s, j + 1);
                        ((List) s).set(j, obj);
                    } else {
                        ((Map) s).put(lastPart, obj);
                    }
                }

                @Override
                public void append(List<?> array) {
                    if (s instanceof List) {
                        Alias.this.append(array, Integer.parseInt(lastPart), (List) s);
                    } else {
                        List dst = (List) ((Map) s).computeIfAbsent(lastPart, k -> new ArrayList<>());
                        dst.addAll(array);
                    }
                }
            };
        }

        private Object createNode(String[] pathParts, int pos) {
            switch (pathParts[pos + 1]) {
                case "i64":
                case "i32":
                case "i16":
                case "i8":
                    try {
                        Integer.parseInt(pathParts[pos + 2].split("\\?")[0]);
                    } catch (NumberFormatException e) {
                        return new HashMap<>();
                    }
                    return new ArrayList<>();
                default:
                    if (pathParts[pos].contains("@")) {
                        String[] withProcessor = pathParts[pos].split("@");

                        return new FormatParser.DataWrapper(new ArrayList<>()) {
                            @Override
                            public String toString() {
                                return processors.get(withProcessor[1]).write(data);
                            }
                        };
                    }
            }
            return new HashMap<>();
        }

        public void write(Object serialized, Map<String, Object> params, Object data) {
            if (structs.containsKey("#debug")) {
                System.out.println("Debug write: " + path);
            }
            String path = this.path;
            writeWithPath(serialized, params, data, path, "");
        }

        private void writeWithPath(Object serialized, Map<String, Object> params, Object data, String path, String fullPath) {
            if (path.startsWith("#")) {
                path = path.substring(1);
                // TODO extra params
                if (path.endsWith("]")) {
                    Matcher matcher = ARRAY.matcher(path);
                    if (!matcher.find()) {
                        throw new IllegalArgumentException("Wrong field name: " + path);
                    }
                    String[] split = matcher.group(2).split(":");
                    String struct = split[0];
                    path = matcher.group(1);
                    List array = writeArray(data, params, struct, fullPath + "/" + path);
                    Put put = forPut(path, params, serialized, fullPath);
                    if (split.length > 1) {
                        put.append(array);
                    } else {
                        put.set(array);
                    }
                    return;
                }
                String struct = path;
                write(serialized, params, data, struct, fullPath + "/" + path);
                return;
            }
            forPut(path, params, serialized, fullPath).set(data);
        }

        private List<?> writeArray(Object data, Map<String, Object> params, String structName, String fullPath) {
            List r = new ArrayList();
            int i = 0;
            for (Object obj : (List) data) {
                Map m = new HashMap();
                write(m, params, obj, structName, fullPath + "/" + i);
                r.add(m);
                i++;
            }
            return r;
        }

        private void write(Object serialized, Map<String, Object> params, Object data, String structName, String fullPath) {
            Map<String, Object> struct = structs.get(structName);
            Object vars = ((Map<?, ?>) struct).get("#vars");
            if (vars != null) {
                Map nParams = new HashMap<>(params);
                for (Map.Entry<String, String> entry : ((Map<String, String>) vars).entrySet()) {
                    nParams.put(evalString(entry.getKey(), params, stdFunctions), evalString(entry.getValue(), params, stdFunctions));
                }
                params = nParams;
            }
            for (Map.Entry<String, Object> entry : struct.entrySet()) {
                String alias = entry.getKey();
                if (alias.startsWith("#")) {
                    continue;
                }
                String path = (String) entry.getValue();

                Matcher matcher = ARRAY.matcher(alias);
                if (matcher.find()) {
                    String n = matcher.group(1);
                    List lst = (List) ((Map) data).get(n);
                    long i = 0;
                    params = new HashMap<>(params);
                    for (Object obj : lst) {
                        params.put("i", i++);
                        writeWithPath(serialized, params, obj, path, fullPath);
                    }
                    forPut(path, params, serialized, fullPath);
                } else {
                    writeWithPath(serialized, params, ((Map) data).get(alias), path, fullPath);
                }

            }
        }
    }

    private static class LocalizedString extends HashMap<String, String> {

        String get(Context context) {
            if (containsKey("link")) {
                Map<String, Object> params = context.params();
                LocalizedString link = (LocalizedString) Interpreter.eval(get("link"), params, stdFunctions);
                return link.get("Ru");
            }
            String result = get("Ru");
            if (result == null) {
                return "";
            }
            if (context != null && result.contains("${")) {
                return evalString(result, context.params(), stdFunctions);
            }
            return result;
        }
    }

    private static class Value extends LocalizedString {

        String value() {
            return get("value");
        }

        @Override
        public String toString() {
            return get(null);
        }
    }

    private static class Group {
        LocalizedString name = new LocalizedString();
        List<Field> fields = new ArrayList<>();
    }

    private static class Field extends Group {
        FieldType type;
        String option;
        String widthSample;
        boolean fillWidth;
        int maxWidth;
        boolean breaks;
        List<Value> values;
        String id;
        String dependencyField;
        Map<String, Field> cases;
        String link;
        Map<String, Object> vars;
        int min;
        int max;

        transient String idSuffix;
    }

    private static class TabGroup extends Group {
        TabType type;
        String id;
        String title;
        String alias;
        List<TabGroup> tabs;
    }

    private static class Filter {
        String regex;
        String field;
    }

    enum TabType {
        multiple,
        join
    }

    enum FieldType {
        integer,
        string,
        text,
        combo,
        chips,
        group,
        dependent,
        struct,
        array,
        nothing,
        hex
    }

    private static final Pattern ARRAY = Pattern.compile("(.*)\\[(.*)]");

    private static int intFromList(List<Integer> lst, int offset) {
        int t = offset;

        int v4 = lst.get(t++);
        int v3 = lst.get(t++);
        int v2 = lst.get(t++);
        int v1 = lst.get(t++);

        return v1 << 24 | v2 << 16 | v3 << 8 | v4;
    }


    private static final Pattern EVAL_PATTERN = Pattern.compile("\\$\\{([^}]*)}");

    private static String evalString(String str, Map<String, ?> params, Map<String, Function<List<Object>, Object>> functions) {
        if (str.contains("${")) {
            Matcher m = EVAL_PATTERN.matcher(str);
            StringBuilder builder = new StringBuilder();
            while (m.find()) {
                Object e = Interpreter.eval(m.group(1), params, functions);
                m.appendReplacement(builder, String.valueOf(e).replace("$", "\\$"));
            }
            m.appendTail(builder);
            return builder.toString();
        }
        return str;
    }

    private static class DelayedNode {
        final StackPane parent;
        final Field field;
        final Deque<String> path;
        final Deque<Map<String, ?>> vars;

        private DelayedNode(StackPane parent, Field field, Deque<String> path, Deque<Map<String, ?>> vars) {
            this.parent = parent;
            this.field = field;
            this.path = path;
            this.vars = vars;
        }
    }

    private static abstract class Entry {
        final TabGroup group;
        final Map<String, Object> data;
        final Supplier<String> name;

        String title;

        Entry(TabGroup group, Map<String, Object> data, Supplier<String> name) {
            this.group = group;
            this.data = data;
            this.name = name;

            title = name.get();
        }

        @Override
        public String toString() {
            return title;
        }

        public abstract void remove();

        public abstract Entry copy();
    }

    private static <T extends Region> T paint(T c) {
        //c.setBackground(new Background(new BackgroundFill(Color.rgb(ThreadLocalRandom.current().nextInt(256), ThreadLocalRandom.current().nextInt(256), ThreadLocalRandom.current().nextInt(256)), null, null)));
        return c;
    }

    Insets padding = new Insets(2, 2, 2, 2);

    private List<Node> parse(Field field, Context context) {
        context.push(field);
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
                case array: {
                    Matcher matcher = ARRAY.matcher(field.link);
                    if (!matcher.find()) {
                        throw new IllegalArgumentException("Wrong array field");
                    }
                    Field link = matcher.group(1).isEmpty() ? null : context.structs().get(matcher.group(1));
                    if (!matcher.group(1).isEmpty() && link == null) {
                        throw new IllegalArgumentException("No such ref: " + matcher.group(1));
                    }
                    String[] group = matcher.group(2).split(",");
                    String countGroup = evalString(group[group.length - 1], context.params(), stdFunctions);
                    boolean allowAdd;
                    int count;
                    Object contextValue = context.currentValue();
                    if (countGroup.isEmpty()) {
                        count = ((List) contextValue).size();
                        allowAdd = true;
                    } else {
                        count = Integer.parseInt(countGroup);
                        allowAdd = false;
                    }
                    int start = group.length > 1 ? Integer.parseInt(group[0]) : 0;
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
                        Button e = new Button();
                        e.setText("+");
                        e.setOnAction(a -> ((List) contextValue).add(new HashMap<>()));

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
                case group: {
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
                        label.setText(field.name.get(context));
                        label.setLabelFor(content);
                        label.getStyleClass().add("bordered-titled-title");

                        StackPane.setAlignment(label, Pos.TOP_CENTER);
                        pane.getChildren().setAll(content, label);
                        if (field.maxWidth != 0) {
                            pane.setMaxWidth(field.maxWidth);
                        }
                        label.setOnMouseClicked(new EventHandler<>() {
                            boolean stateChanged = false;
                            Property<Object> obj = context.currentProperty();
                            TextArea area = new TextArea();

                            {
                                area.getStyleClass().add("bordered-titled-content");
                                area.setMinWidth(400);
                                area.setPrefWidth(400);
                                area.setFont(Font.font("monospace"));
                            }

                            @Override
                            public void handle(MouseEvent e) {
                                if (e.getClickCount() < 2) {
                                    return;
                                }
                                if (stateChanged) {
                                    pane.getChildren().set(0, content);
                                    obj.setValue(gson.fromJson(area.getText(), Object.class));
                                } else {
                                    area.setText(gson.toJson(obj.getValue()));
                                    pane.getChildren().set(0, area);
                                }
                                pane.requestLayout();
                                stateChanged = !stateChanged;
                            }
                        });
                        return Collections.singletonList(pane);
                    }
                    if ("line".equals(field.option)) {
                        HBox content = new HBox();
                        for (Field f : field.fields) {
                            content.getChildren().addAll(parse(f, context));
                        }
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

                    TitledPane pane = new TitledPane();
                    TitledPaneSkin skin = new TitledPaneSkin(pane) {
                        {
                            Node label = getChildren().get(1);
                            label.setOnMouseClicked(new EventHandler<>() {
                                boolean stateChanged = false;
                                Property<Object> obj = context.currentProperty();
                                TextArea area = new TextArea();

                                {
                                    area.getStyleClass().add("bordered-titled-content");
                                    area.setMinWidth(400);
                                    area.setPrefWidth(400);
                                    area.setMinHeight(400);
                                    area.setPrefHeight(400);
                                    area.setFont(Font.font("monospace"));
                                }

                                @Override
                                public void handle(MouseEvent e) {
                                    if (e.getClickCount() < 2) {
                                        return;
                                    }
                                    if (stateChanged) {
                                        pane.setContent(box);
                                        obj.setValue(gson.fromJson(area.getText(), Object.class));
                                    } else {
                                        area.setText(gson.toJson(obj.getValue()));
                                        pane.setContent(area);
                                    }
                                    pane.requestLayout();
                                    stateChanged = !stateChanged;
                                    e.consume();
                                }
                            });
                        }

                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
                            // fixes bug of using padding in computation
                            return super.computePrefHeight(width - rightInset - leftInset, topInset, rightInset, bottomInset, leftInset);
                        }
                    };
                    pane.setSkin(skin);
                    pane.setAnimated(false);
                    pane.setCollapsible(field.fillWidth);
                    pane.setText(field.name.get(context));
                    pane.setContent(box);
                    if (field.maxWidth != 0) {
                        box.setMaxWidth(field.maxWidth);
                    }
                    pane.setPadding(padding);
                    return Collections.singletonList(pane);
                }
                case string: {
                    VBox pane = new VBox();
                    Label label = new Label(field.name.get(context));
                    paint(label);
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
                    return Collections.singletonList(pane);
                }
                case integer: {
                    VBox pane = new VBox();
                    Label label = new Label(field.name.get(context));
                    paint(label);
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
                    return Collections.singletonList(pane);
                }
                case text: {
                    VBox pane = new VBox();
                    Label label = new Label(field.name.get(context));
                    paint(label);
                    TextArea textField = new TextArea();
                    textField.setWrapText(true);
                    if (field.id != null) {
                        context.refs.put(field.id, textField.textProperty());
                    }
                    if (field.widthSample != null) {
                        textField.setPrefColumnCount(field.widthSample.length());
                    }
                    if (field.maxWidth != 0) {
                        pane.setMaxWidth(field.maxWidth);
                    }
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
                    return Collections.singletonList(pane);
                }
                case hex: {
                    VBox pane = new VBox();
                    Label label = new Label(field.name.get(context));
                    paint(label);
                    TextArea textField = new TextArea();
                    textField.setWrapText(true);
                    textField.setFont(Font.font("monospace"));
                    if (field.id != null) {
                        context.refs.put(field.id, textField.textProperty());
                    }
                    textField.setPrefColumnCount(32);


                    Property<Object> obj = context.currentProperty();
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


                    return Collections.singletonList(pane);
                }
                case chips: {
                    VBox pane = new VBox();
                    paint(pane);
                    Label label = new Label(field.name.get(context));
                    paint(label);
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
                    if (field.maxWidth != 0) {
                        pane.setMaxWidth(field.maxWidth);
                        pane.setPrefWidth(field.maxWidth);
                    }
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
                    return Collections.singletonList(pane);
                }
                case combo: {
                    VBox pane = new VBox();
                    paint(pane);
                    Label label = new Label(field.name.get(context));
                    paint(label);
                    pane.getChildren().add(label);

                    ComboBox<Value> textField = new ComboBox<>();

                    textField.getItems().setAll(getItems(field.link, field.values));
                    if (field.maxWidth != 0) {
                        pane.setMaxWidth(field.maxWidth);
                        pane.setPrefWidth(field.maxWidth);
                    }

                    if (field.id != null) {
                        context.refs.put(field.id, Bindings.createStringBinding(
                                () -> textField.getValue() == null ? "null" : textField.getValue().value(),
                                textField.valueProperty()
                        ));
                    }
                    StringProperty property = new SimpleStringProperty();
                    property.bindBidirectional(textField.valueProperty(), new StringConverter<>() {
                        @Override
                        public String toString(Value object) {
                            return object == null ? null : object.value();
                        }

                        @Override
                        public Value fromString(String string) {
                            for (Value item : textField.getItems()) {
                                if (Objects.equals(String.valueOf(item.value()), string)) {
                                    return item;
                                }
                            }
                            return null;
                        }
                    });
                    context.addProperty(property);

                    if (textField.getItems().size() > 8) {
                        SearchableComboBoxSkin<Value> value = new SearchableComboBoxSkin<>(textField);
                        CustomTextField node = (CustomTextField) value.getChildren().get(1);
                        node.setLeft(null);
                        textField.setSkin(value);
                    }

                    pane.getChildren().add(textField);
                    pane.setPadding(padding);
                    return Collections.singletonList(pane);
                }
                case dependent: {
                    StackPane node = new StackPane();
                    context.delayedNodes.add(new DelayedNode(node, field, new ArrayDeque<>(context.path), new ArrayDeque<>(context.vars)));
                    return Collections.singletonList(node);
                }
            }
            throw new IllegalStateException();
        } catch (Exception e) {
            throw new RuntimeException("Field: " + String.join("/", context.path), e);
        } finally {
            context.pop(field);
        }
    }

    private FlowPane createFlow() {
        FlowPane currentFlow = new FlowPane();
        currentFlow.setPadding(padding);
        currentFlow.setPrefWrapLength(800);
        paint(currentFlow);
        return currentFlow;
    }

    private VBox parse(List<Field> fields, Context context) {
        VBox content = new VBox();
        paint(content);
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
                if (f.fillWidth || field.fillWidth) {
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

    final Ui ui;
    final Format format;
    final Map<String, Object> toSerialize = new LinkedHashMap<>();

    private Map<String, List<Value>> dynamicEnums;

    public Gui(Path cfg, String format) throws IOException {
        try (Reader reader = Files.newBufferedReader(cfg)) {
            ui = gson.fromJson(reader, Ui.class);
        }
        for (Format value : ui.formats.values()) {
            for (Alias alias : value.aliases.values()) {
                alias.processors = value.processors;
            }
        }
        this.format = ui.formats.get(format);
    }


    private Map<String, List<Value>> dynamicEnums() {
        if (dynamicEnums == null) {
            dynamicEnums = new HashMap<>();
            for (Map.Entry<String, DynamicEnum> entry : ui.dynamicEnums.entrySet()) {
                DynamicEnum value = entry.getValue();
                List data = (List) toSerialize.get(value.fromTab);
                List<Value> values = new ArrayList<>();
                for (Object d : data) {
                    Map<String, Object> params = new HashMap<>();
                    params.putAll((Map) d);
                    Value r = new Value();
                    for (Map.Entry<String, String> v : value.template.entrySet()) {
                        r.put(evalString(v.getKey(), params, stdFunctions), evalString(v.getValue(), params, stdFunctions));
                    }
                    values.add(r);
                }
                dynamicEnums.put(entry.getKey(), values);
            }
        }
        return dynamicEnums;
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            primaryStage.setTitle("Data Editor");
            StackPane root = new StackPane();

            MenuButton fileMenu = new MenuButton("File");
            MenuItem save = new MenuItem("Save");
            save.setAccelerator(KeyCombination.valueOf("Ctrl+S"));
            save.setOnAction(event -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Resource File");
                File file = fileChooser.showSaveDialog(primaryStage);

                try (OutputStreamWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                    gson.toJson(toSerialize, writer);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            fileMenu.getItems().add(save);
            MenuItem open = new MenuItem("Open");
            open.setAccelerator(KeyCombination.valueOf("Ctrl+O"));
            open.setOnAction(event -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Open Resource File");
                File file = fileChooser.showOpenDialog(primaryStage);
                try (InputStreamReader input = new FileReader(file, StandardCharsets.UTF_8)) {
                    Object p = gson.fromJson(input, Object.class);
                    toSerialize.clear();
                    initTabs(p, null, toSerialize, root);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            fileMenu.getItems().add(open);
            MenuItem export = new MenuItem("Export");
            export.setAccelerator(KeyCombination.valueOf("Shift+Ctrl+S"));
            export.setOnAction(event -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Export Resource File");
                File file = fileChooser.showSaveDialog(primaryStage);
                Object serialized = new HashMap<>();

                ui.tabs.stream().flatMap(t -> t.type == TabType.join ? t.tabs.stream() : Stream.of(t)).forEach(tabGroup -> {
                    Object data = toSerialize.get(tabGroup.id);

                    Alias alias = format.aliases.get(tabGroup.alias);
                    alias.write(serialized, Collections.emptyMap(), data);

                });

                try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
                    FormatParser.write(out, serialized, format.template, format.structs);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            fileMenu.getItems().add(export);
            MenuItem _import = new MenuItem("Import");
            _import.setAccelerator(KeyCombination.valueOf("Shift+Ctrl+O"));
            _import.setOnAction(event -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Import Resource File");
                File file = fileChooser.showOpenDialog(primaryStage);
                Object p;
                Format f = ui.formats.get("dat");
                try (DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file))) {
                    p = FormatParser.parse(dataInputStream, f.template, f.structs);
                    toSerialize.clear();
                    initTabs(p, f, toSerialize, root);
                    primaryStage.sizeToScene();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            fileMenu.getItems().add(_import);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());

            StackPane.setAlignment(fileMenu, Pos.TOP_LEFT);
            StackPane.setMargin(fileMenu, padding);
            root.getChildren().add(fileMenu);

            primaryStage.setScene(scene);
            primaryStage.setMinWidth(600);
            primaryStage.setMinHeight(400);
            primaryStage.sizeToScene();
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initTabs(Object parsed, Format format, Map<String, Object> toSerialize, StackPane root) {

        if (format == null) {
            toSerialize.putAll((Map<? extends String, ?>) parsed);
        }

        TabPane tabs = new TabPane();
        root.getChildren().removeIf(n -> n instanceof TabPane);
        root.getChildren().add(0, tabs);
        for (TabGroup tabGroup : ui.tabs) {
            Tab tab = getTab(parsed, format, toSerialize, tabGroup);
            tabs.getTabs().add(tab);
        }

        VBox editor = new VBox();
        editor.setPadding(padding);

        HBox common = new HBox();
        editor.getChildren().add(common);
        TextField idRegex = withLabel(common, "ID regex", TextField::new);
        TextField idSample = withLabel(common, "ID sample", TextField::new);
        TextField title = withLabel(common, "Title", TextField::new);
        TextField nameRu = withLabel(common, "Name (ru)", TextField::new);
        TextField nameEng = withLabel(common, "Name (eng)", TextField::new);

        Field nextField = new Field();
        editor.getChildren().add(createGroupBase(nextField, new VBox()));

        Button add = new Button();
        add.setText("+Tab");

        add.setOnAction(a -> {
            TabGroup group = new TabGroup();
            group.type = TabType.multiple;
            group.id = createId(nameEng.getText());
            group.alias = group.id;
            group.title = title.getText();
            group.fields = nextField.fields;
            group.name.put("Eng", nameEng.getText());
            group.name.put("Ru", nameRu.getText());

            Alias alias = new Alias();
            ui.formats.get("dat").aliases.put(group.alias, alias);

            Tab tab = getTab(parsed, format, toSerialize, group);
            tabs.getTabs().add(tabs.getTabs().size() - 1, tab);
            ui.tabs.add(group);
            tabs.requestLayout();

            System.out.println(gson.toJson(group));
        });

        editor.getChildren().add(add);

        Tab editorTab = new Tab("Editor");
        editorTab.setClosable(false);
        ScrollPane editorWrapper = new ScrollPane(editor);
        editorWrapper.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        editorTab.setContent(editorWrapper);
        tabs.getTabs().add(editorTab);
    }

    private Tab getTab(Object parsed, Format format, Map<String, Object> toSerialize, TabGroup tabGroup) {
        Tab tab = new Tab(tabGroup.name.get(null));
        tab.setClosable(false);

        HBox hBox = new HBox();
        VBox listParent = new VBox();
        listParent.setPrefWidth(250);
        StackPane parent = new StackPane();
        parent.setPrefWidth(1000);
        HBox.setHgrow(parent, Priority.ALWAYS);
        hBox.getChildren().addAll(listParent, parent);
        tab.setContent(hBox);

        if (tabGroup.type == TabType.multiple) {
            ListView<Entry> listView = getListView(parent, getEntries(parsed, format, tabGroup));
            VBox.setVgrow(listView, Priority.ALWAYS);
            listParent.getChildren().add(listView);
        } else if (tabGroup.type == TabType.join) {
            List<Entry> common = new ArrayList<>();
            for (TabGroup group : tabGroup.tabs) {
                List<Entry> current = getEntries(parsed, format, group);
                if (current.size() <= 4) {
                    common.addAll(current);
                } else {
                    String name = group.name.get(null);
                    if (name.isEmpty()) {
                        name = group.title;
                    }
                    ListView<Entry> list = getListView(parent, current);
                    TitledPane pane = new TitledPane(name, list);
                    pane.setAnimated(false);
                    listParent.getChildren().add(pane);
                }
            }
            if (common.size() > 0) {
                if (listParent.getChildren().size() > 0) {
                    ListView<Entry> list = getListView(parent, common);
                    TitledPane pane = new TitledPane("Other", list);
                    pane.setAnimated(false);
                    listParent.getChildren().add(pane);
                } else {
                    ListView<Entry> listView = getListView(parent, common);
                    VBox.setVgrow(listView, Priority.ALWAYS);
                    listParent.getChildren().add(listView);
                }
            }
        }

        return tab;
    }

    private List<Entry> getEntries(Object parsed, Format format, TabGroup tabGroup) {
        try {
            Object data;
            if (format != null) {
                Alias alias = format.aliases.get(tabGroup.alias);
                data = alias.read(parsed, ui);
                toSerialize.put(tabGroup.id, data);
            } else {
                data = toSerialize.computeIfAbsent(tabGroup.id, k -> new ArrayList<>());
            }

            List<Entry> result = new ArrayList<>();

            List<Object> lst = (List<Object>) data;
            for (Object obj : lst) {
                Map<String, Object> m = (Map<String, Object>) obj;

                class E extends Entry {
                    E(Map<String, Object> data) {
                        super(tabGroup, data, () -> evalString(tabGroup.title, m, stdFunctions));
                    }
                    @Override
                    public void remove() {
                        lst.removeIf(o -> m == o);
                    }

                    @Override
                    public Entry copy() {
                        Object mm = gson.fromJson(gson.toJson(m), Object.class);
                        lst.add(mm);
                        return new E((Map<String, Object>) mm);
                    }
                }

                result.add(new E(m));
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException("Tab: " + tabGroup.id, e);
        }
    }

    private ListView<Entry> getListView(StackPane parent, List<Entry> entries) {
        ListView<Entry> listView = new ListView<>();
        listView.getItems().addAll(entries);
        listView.setPrefHeight(10000);

        listView.setEditable(true);
        listView.setCellFactory(l -> {
            return new ListCell<>() {
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
                        getItem().title = getItem().name.get();
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
                }
            };
        });
        ContextMenu menu = new ContextMenu();
        MenuItem delete = new MenuItem("Delete");
        MenuItem copy = new MenuItem("Copy");
        delete.setOnAction(actionEvent -> {
            int index = listView.getSelectionModel().getSelectedIndex();
            Entry entry = listView.getItems().get(index);
            entry.remove();
            listView.getItems().remove(index);
        });
        copy.setOnAction(actionEvent -> {
            int index = listView.getSelectionModel().getSelectedIndex();
            Entry entry = listView.getItems().get(index);
            listView.getItems().add(entry.copy());
        });
        menu.getItems().addAll(copy, delete);
        listView.setContextMenu(menu);
        listView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    Context context = new Context(newValue.data);
                    parent.getChildren().clear();

                    Parent content = parseTab(newValue.group, context);
                    parent.getChildren().add(content);
                    finishContext(context);
                }
        );
        return listView;
    }

    private <T extends Node> T withLabel(Pane parent, String label, Supplier<T> supplier) {
        T field = supplier.get();
        Label lab = new Label(label);
        lab.setLabelFor(field);
        VBox box = new VBox(lab, field);
        box.setPadding(padding);
        parent.getChildren().add(box);
        return field;
    }

    private Node withLabel(String label, Supplier<? extends Node> supplier) {
        Node field = supplier.get();
        Label lab = new Label(label);
        lab.setLabelFor(field);
        VBox box = new VBox(lab, field);
        box.setPadding(padding);
        return box;
    }

    private Node createFields(Field field) {
        TitledPane pane = new TitledPane();
        TitledPaneSkin skin = new TitledPaneSkin(pane) {
            @Override
            protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
                // fixes bug of using padding in computation
                return super.computePrefHeight(width - rightInset - leftInset, topInset, rightInset, bottomInset, leftInset);
            }
        };
        pane.setSkin(skin);
        pane.setAnimated(false);
        pane.setCollapsible(true);
        pane.setText("Field");
        pane.setPadding(padding);

        FlowPane box = new FlowPane();
        VBox fields = new VBox();
        fields.getChildren().add(box);

        TextField nameRu = withLabel(box, "Name (ru)", TextField::new);
        TextField nameEng = withLabel(box, "Name (eng)", TextField::new);

        nameRu.textProperty().addListener((observable, oldValue, newValue) -> {
            field.name.put("Ru", newValue);
            pane.setText(field.name.get(null));
        });
        nameEng.textProperty().addListener((observable, oldValue, newValue) -> {
            field.name.put("Eng", newValue);
            pane.setText(field.name.get(null));
            field.id = createId(newValue);
        });

        ComboBox<String> type = withLabel(box, "Type", ComboBox::new);

        StackPane stackPane = new StackPane();
        fields.getChildren().add(stackPane);

        new ComboItems(type, stackPane, field)
                .item("String", this::createStringField)
                .item("Integer", this::createIntegerField)
                .item("Text", this::createTextAreaField)
                .item("Combo", this::createCombo)
                .item("Chips", this::createChips)
                .item("Group", this::createGroup)
                .item("Array", this::createArray)
        ;


        pane.setContent(fields);

        return pane;
    }

    private String createId(String newValue) {
        return String.join("_", newValue.toLowerCase().split(" .+"));
    }

    private Node createStringField(Field field) {
        TextField textField = new TextField();
        textField.textProperty().addListener((observable, oldValue, newValue) -> field.widthSample = newValue);
        return withLabel("Sample value", () -> new HBox(textField));
    }

    private Node createIntegerField(Field field) {
        return withLabel("Sample value", () -> {
            Spinner<Integer> spinner = new Spinner<>(0, 10000, 0);
            spinner.setEditable(true);
            spinner.getEditor().textProperty().addListener((observable, oldValue, newValue) -> field.widthSample = newValue);
            return spinner;
        });
    }

    private Node createTextAreaField(Field field) {
        return withLabel("Sample value", () -> new HBox(new TextArea()));
    }

    private List<Value> getItems(String link, List<Value> values) {
        List<Value> result = new ArrayList<>(values == null ? Collections.emptyList() : values);
        if (link != null && !link.isEmpty()) {
            result.addAll(ui.enums.getOrDefault(link, Collections.emptyList()));
            result.addAll(dynamicEnums().getOrDefault(link, Collections.emptyList()));
        }
        return result;
    }

    private HBox createComboBase(Field field, Consumer<List<Value>> invalidateAction) {
        HBox root = new HBox();

        ListView<String> link = withLabel(root, "Basic items", ListView::new);
        link.getItems().add("");
        link.getItems().addAll(ui.enums.keySet());
        link.getItems().addAll(ui.dynamicEnums.keySet());

        ListView<Value> values = withLabel(root, "Custom items", ListView::new);

        VBox valueText = new VBox();
        TextField textRu = withLabel(valueText, "Ru", TextField::new);
        TextField textEng = withLabel(valueText, "Eng", TextField::new);
        Spinner<Integer> value = withLabel(valueText, "Value", () -> new Spinner<>(0, 10000, 0));
        value.setEditable(true);

        values.getSelectionModel().selectedItemProperty().addListener((o, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            textRu.setText(newValue.get("Ru"));
            textEng.setText(newValue.get("Eng"));
            value.getEditor().setText(newValue.value());
        });

        Button add = new Button();
        add.setText("+");

        add.setOnAction(a -> {
            Value v = new Value();
            v.put("Ru", textRu.getText());
            v.put("Eng", textEng.getText());
            v.put("value", value.getValue().toString());

            textRu.setText("");
            textEng.setText("");
            value.getEditor().setText("0");
            values.getItems().add(v);

            textRu.requestFocus();

            field.values.add(v);
        });

        Button set = new Button();
        set.setText("set");
        set.setOnAction(a -> {
            Value v = values.getSelectionModel().getSelectedItem();

            v.put("Ru", textRu.getText());
            v.put("Eng", textEng.getText());
            v.put("value", value.getValue().toString());

            values.getItems().set(values.getSelectionModel().getSelectedIndex(), v);
            values.requestFocus();
        });

        ButtonBar bar = new ButtonBar();
        bar.getButtons().setAll(set, add);
        bar.setPadding(padding);

        valueText.getChildren().add(bar);

        root.getChildren().add(valueText);

        link.getSelectionModel().selectedItemProperty().addListener(c -> invalidateAction.accept(getItems(link.getSelectionModel().getSelectedItem(), values.getItems())));
        values.getItems().addListener((ListChangeListener<Value>) c -> invalidateAction.accept(getItems(link.getSelectionModel().getSelectedItem(), values.getItems())));

        return root;
    }

    private Node createCombo(Field field) {
        ComboBox<Value> sample = new ComboBox<>();
        sample.setPrefWidth(150);

        HBox root = createComboBase(field, values -> sample.getItems().setAll(values));
        withLabel(root, "Sample value", () -> sample);

        return root;
    }

    private Node createChips(Field field) {
        JFXChipView<Value> sample = new JFXChipView<>(){
            @Override
            public Orientation getContentBias() {
                return Orientation.HORIZONTAL;
            }
        };
        sample.setPrefWidth(300);
        sample.setMinWidth(300);
        sample.setMaxWidth(300);
        sample.setSkin(new JFXChipViewSkin<>(sample));
        sample.setPredicate((value, s) -> value.toString().toLowerCase().startsWith(s.toLowerCase()) && !sample.getChips().contains(value));

        HBox root = createComboBase(field, values -> sample.getSuggestions().setAll(values));
        withLabel(root, "Sample value", () -> sample);

        return root;
    }

    private Node createGroup(Field field) {
        VBox root = new VBox();
        createGroupBase(field, root);
        return root;
    }

    private Node createArray(Field field) {
        VBox root = new VBox();
        Spinner<Integer> fixedSize = withLabel(root, "Fixed size", () -> new Spinner<>(-1, 10000, -1));
        fixedSize.valueProperty().addListener((observable, oldValue, newValue) ->
                field.idSuffix = newValue == -1 ? null : ("[" + newValue + "]")
        );

        createGroupBase(field, root);
        return root;
    }

    private VBox createGroupBase(Field field, VBox root) {
        Button add = new Button();
        add.setText("+Field");
        root.getChildren().add(add);

        add.setOnAction(a -> {
            Field ff = new Field();
            field.fields.add(ff);
            root.getChildren().add(root.getChildren().size() - 1, createFields(ff));
        });
        add.getOnAction().handle(null);
        return root;
    }

    private static class ComboItems {
        final ComboBox<String> comboBox;
        final StackPane pane;
        final Field field;
        final Map<String, Runnable> items = new HashMap<>();

        ComboItems(ComboBox<String> comboBox, StackPane pane, Field field) {
            this.comboBox = comboBox;
            this.pane = pane;
            this.field = field;
            this.comboBox.valueProperty().addListener((observable, oldValue, newValue) -> items.get(newValue).run());
        }

        ComboItems item(String name, Function<Field, Node> nodeCreator) {
            comboBox.getItems().add(name);
            items.put(name, new Runnable() {

                Node node;

                @Override
                public void run() {
                    field.type = FieldType.valueOf(name.toLowerCase());
                    if (node == null) {
                        node = nodeCreator.apply(field);
                    }
                    pane.getChildren().setAll(node);
                    pane.requestLayout();
                }
            });
            return this;
        }
    }

    private void finishContext(Context context) {
        for (DelayedNode delayedNode : context.delayedNodes) {
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
                    context.vars.clear();
                    context.vars.addAll(delayedNode.vars);
                    context.path.clear();
                    context.path.addAll(delayedNode.path);
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

        return content;
    }

    private static String string(Object o) {
        if (o instanceof List) {
            List<Integer> t = (List<Integer>) o;
            byte[] data = new byte[t.size()];
            for (int i = 0; i < t.size(); i++) {
                data[i] = t.get(i).byteValue();
            }
            return new String(data, Charset.forName("windows-1251"));
        }
        return String.valueOf(o);
    }

    private static String path(Context context) {
        String path = String.join("/", (Iterable<String>) context.path::descendingIterator);
        if (path.contains("${")) {
            Map<String, Object> params = new HashMap<>();
            for (Map<String, ?> var : context.vars) {
                params.putAll(var);
            }
            path = evalString(path, params, null);
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

    private class Context {
        Map<String, List<StringProperty>> properties = new HashMap<>();
        final Map<String, ObservableValue<?>> refs = new HashMap<>();
        final List<DelayedNode> delayedNodes = new ArrayList<>();

        final Deque<Map<String, ?>> vars = new ArrayDeque<>();
        final Deque<String> path = new ArrayDeque<>();

        private Map<String, Object> data;

        private Context(Map<String, Object> localData) {
            vars.push(ui.enums);
            vars.push(dynamicEnums());
            this.data = localData;
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

                            long v2 = 0xffffffffL & intFromList((List<Integer>) d, t);
                            long v1 = 0xffffffffL & intFromList((List<Integer>) d, t + 4);

                            d = v1 << 32 | v2;
                            break;
                        }
                        case "i32": {
                            int t = Integer.parseInt(path[i++]);
                            d = intFromList((List<Integer>) d, t);
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
                lst.set(t, value);
            }
        }

        public Object currentValue() {
            String key = path(this);
            String[] path = key.split("/");
            return get(data, path);
        }

        public Property<Object> currentProperty() {
            String key = path(this);
            String[] path = key.split("/");

            Property<Object> result = new SimpleObjectProperty<>();
            result.setValue(get(data, path));
            result.addListener((observable, oldValue, newValue) -> set(data, path, newValue));
            return result;
        }

        public void addProperty(StringProperty property) {
            String key = path(this);
            String[] path = key.split("/");
            properties.computeIfAbsent(key, k -> new ArrayList<>()).add(property);
            property.setValue(string(get(data, path)));
            property.addListener((observable, oldValue, newValue) -> {
                dynamicEnums = null;
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
                return evalString((String) data, params, null);
            }
            if (data instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) data;
                Map<String, Object> res = new HashMap<>(m.size());
                for (Map.Entry<String, Object> e : m.entrySet()) {
                    res.put(evalString(e.getKey(), params, null), evaluate(e.getValue(), params));
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
            return ui.structs;
        }
    }

}