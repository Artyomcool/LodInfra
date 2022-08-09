package com.github.artyomcool.lodinfra.data;

import com.github.artyomcool.lodinfra.data.dto.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;

@SuppressWarnings({"unchecked", "rawtypes"})
public class AliasProcessor {

    final IdentityHashMap<Object, Object> parsedCache = new IdentityHashMap<>();
    final Map<String, ProcessorWrapper> processors;
    final Alias data;

    public AliasProcessor(Map<String, ProcessorWrapper> processors, Alias data) {
        this.processors = processors;
        this.data = data;
    }

    @SuppressWarnings("rawtypes")
    private Object read(Object parsed, Map<String, ?> params, String path, int arraySize, String fullPath) {
        if (path == null) {
            return parsed;
        }
        if (path.startsWith("#")) {
            path = path.substring(1);
            // TODO extra params
            if (path.endsWith("]")) {
                Matcher matcher = Helpers.ARRAY.matcher(path);
                if (!matcher.find()) {
                    throw new IllegalArgumentException("Wrong field name: " + path);
                }

                path = matcher.group(1);
                String struct = matcher.group(2);
                String[] substruct = struct.split(":");
                struct = substruct[0];
                Filter filter = substruct.length > 1 ? data.filters.get(substruct[1]) : null;

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
                        if (!Helpers.string(t).matches(filter.regex)) {
                            continue;
                        }
                        lst.set(i, null);
                    }
                    p.put("i", (long) i);
                    p.put("j", (long) j++);
                    r.add(read(obj, p, data.structs.get(struct), fullPath + "/" + path + "/" + i));
                }
                return r;
            }
            if (arraySize > 0) {
                Map<String, Object> p = new HashMap<>(params);
                List<Object> r = new ArrayList<>(arraySize == Integer.MAX_VALUE ? 16 : arraySize);
                for (int i = 0; i < arraySize; i++) {
                    p.put("i", (long) i);
                    Map<String, Object> read = read(parsed, p, data.structs.get(path), fullPath + "/" + path + "/" + i);
                    if (arraySize == Integer.MAX_VALUE && read == null) {
                        break;
                    }
                    r.add(read);
                }
                return r;
            }
            return read(parsed, params, data.structs.get(path), fullPath + "/" + path);
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
        path = Helpers.evalString(path, params);
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

                        long v2 = 0xffffffffL & Helpers.intFromList((List<Integer>) parsed, t);
                        long v1 = 0xffffffffL & Helpers.intFromList((List<Integer>) parsed, t + 4);

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
                        parsed = String.valueOf(Helpers.intFromList((List<Integer>) parsed, t));
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
            if (parsed instanceof List) {
                int index = Integer.parseInt(withProcessor[0]);
                List<?> lst = (List<?>) parsed;
                parsed = index >= lst.size() ? def : lst.get(index);
            } else {
                parsed = parsed == null ? def : ((Map) parsed).getOrDefault(p[i], def);
            }

            if (withProcessor.length > 1) {
                String str = Helpers.string(parsed);
                parsed = parsedCache.computeIfAbsent(parsed, k -> processRead(processors.get(withProcessor[1]).read, new ReadWrapper(str)));
            }

        }
        if (pathParts.length > 1) {
            switch (pathParts[1]) {
                case "str":
                    parsed = Helpers.string(parsed);
                    break;
                case "bit":
                    String s = BitSet.valueOf(new long[]{Long.parseLong(Helpers.string(parsed).trim())}).toString();
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
                nParams.put(Helpers.evalString(entry.getKey(), params), Helpers.evalString(entry.getValue(), params));
            }
            params = nParams;
        }

        String stop = (String) fields.get("#stopon");

        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            String name = Helpers.evalString(entry.getKey(), params);
            if (name.startsWith("#")) {
                continue;
            }

            String path = (String) entry.getValue();

            Matcher matcher = Helpers.ARRAY.matcher(name);
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

    public List<Map<String, Object>> read(Object parsed, Config cfg) {
        if (data.structs.containsKey("#debug")) {
            System.out.println("Debug read: " + data.path);
        }
        return (List<Map<String, Object>>) read(parsed, cfg.enums, data.path, 0, "");
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
        String[] pathWithType = Helpers.evalString(path, params).split(":");
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
                    AliasProcessor.this.append(array, Integer.parseInt(lastPart), (List) s);
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
                            return processWrite(processors.get(withProcessor[1]).write, data);
                        }
                    };
                }
        }
        return new HashMap<>();
    }

    public void write(Object serialized, Map<String, Object> params, List<DataEntry> data) {
        if (this.data.structs.containsKey("#debug")) {
            System.out.println("Debug write: " + this.data.path);
        }
        String path = this.data.path;
        writeWithPath(serialized, params, data, path, "");
    }

    private void writeWithPath(Object serialized, Map<String, Object> params, Object data, String path, String fullPath) {
        if (path.startsWith("#")) {
            path = path.substring(1);
            // TODO extra params
            if (path.endsWith("]")) {
                Matcher matcher = Helpers.ARRAY.matcher(path);
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
        Map<String, Object> struct = this.data.structs.get(structName);
        Object vars = ((Map<?, ?>) struct).get("#vars");
        if (vars != null) {
            Map nParams = new HashMap<>(params);
            for (Map.Entry<String, String> entry : ((Map<String, String>) vars).entrySet()) {
                nParams.put(Helpers.evalString(entry.getKey(), params), Helpers.evalString(entry.getValue(), params));
            }
            params = nParams;
        }
        for (Map.Entry<String, Object> entry : struct.entrySet()) {
            String alias = entry.getKey();
            if (alias.startsWith("#")) {
                continue;
            }
            String path = (String) entry.getValue();

            Matcher matcher = Helpers.ARRAY.matcher(alias);
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

    static List<String> processRead(Processor read, ReadWrapper reader) {

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

    static String processWrite(WriteProcessor write, List<?> objs) {
        StringBuilder writer = new StringBuilder();
        int[] len = new int[]{0};

        for (Object obj : objs) {
            ReadWrapper text = new ReadWrapper(Helpers.string(obj));

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
                    if (text.data.contains(Helpers.string(object))) {
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
