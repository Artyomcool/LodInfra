package com.github.artyomcool.lodinfra.data;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

public class FormatParser {

    private static final Object NULL = new Object();

    public static abstract class DataWrapper {
        public final List<?> data;

        public DataWrapper(List<?> data) {
            this.data = data;
        }

        @Override
        public abstract String toString();
    }

    public static Object parse(DataInputStream in, String template, Map<String, String> structs) throws IOException {

        Deque<Object> result = new ArrayDeque<>();
        parse(template, structs, new Listener() {

            int pos = 0;

            @Override
            public boolean onOptionalCheck() throws IOException {
                boolean hasValue = in.readBoolean();
                pos++;
                if (!hasValue) {
                    result.push(NULL);
                }
                return hasValue;
            }

            @Override
            public void onI8() throws IOException {
                result.push(in.readUnsignedByte());
                pos++;
            }

            @Override
            public void onI16() throws IOException {
                result.push((int) Short.reverseBytes(in.readShort()));
                pos += 2;
            }

            @Override
            public void onI32() throws IOException {
                result.push(Integer.reverseBytes(in.readInt()));
                pos += 4;
            }

            @Override
            public void onI64() throws IOException {
                result.push(Long.reverseBytes(in.readLong()));
                pos += 8;
            }

            @Override
            public void onFixedData(byte[] bytes) throws IOException {
                for (byte b : bytes) {
                    byte c = in.readByte();
                    if (c != b) {
                        throw new IllegalArgumentException("Unexpected character: " + c + ", expected " + b);
                    }
                    pos++;
                }
                result.push(bytes);
            }

            @Override
            public int onStartArray() throws IOException {
                int size = Integer.reverseBytes(in.readInt());
                pos += 4;
                result.push(Arrays.asList(new Object[size]));
                return size;
            }

            @Override
            public void afterNextArrayField(int index) throws IOException {
                Object value = result.pop();
                if (value != NULL) {
                    ((List) result.getFirst()).set(index, value);
                }
            }

            @Override
            public void onStartStruct() throws IOException {
                result.push(new HashMap<>());
            }

            @Override
            public void afterNextStructField(String name) throws IOException {
                Object value = result.pop();
                if (name.isEmpty()) {
                    return;
                }
                if (value != NULL) {
                    ((Map) result.getFirst()).put(name, value);
                }
            }

        });

        Object r = result.pop();
        return r == NULL ? null : r;
    }

    public static void write(DataOutputStream out, Object serialized, String template, Map<String, String> structs) throws IOException {
        Deque<Object> data = new ArrayDeque<>();
        data.push(serialized == null ? NULL : serialized);
        parse(template, structs, new Listener() {
            @Override
            public boolean onOptionalCheck() throws IOException {
                if (data.peek() == NULL) {
                    data.pop();
                    out.writeBoolean(false);
                    return false;
                }
                out.writeBoolean(true);
                return true;
            }

            @Override
            public void onI8() throws IOException {
                Object pop = data.pop();
                out.writeByte(pop == NULL ? 0 : Integer.parseInt(String.valueOf(pop)));
            }

            @Override
            public void onI16() throws IOException {
                Object pop = data.pop();
                out.writeShort(Short.reverseBytes(pop == NULL ? 0 : Short.parseShort(String.valueOf(pop))));
            }

            @Override
            public void onI32() throws IOException {
                Object pop = data.pop();
                out.writeInt(Integer.reverseBytes(pop == NULL ? 0 : Integer.parseInt(String.valueOf(pop))));
            }

            @Override
            public void onI64() throws IOException {
                Object pop = data.pop();
                out.writeLong(Long.reverseBytes(pop == NULL ? 0 : Long.parseLong(String.valueOf(pop))));
            }

            @Override
            public void onFixedData(byte[] bytes) throws IOException {
                out.write(bytes);
                data.pop();
            }

            private List convertArray(Object array) {
                if (array == NULL) {
                    return Collections.emptyList();
                }
                if (array instanceof List) {
                    return (List) array;
                }
                if (array instanceof Map) {
                    int size = 0;
                    for (Object k : ((Map<?, ?>) array).keySet()) {
                        String s = String.valueOf(k).split("\\?")[0];
                        size = Math.max(Integer.parseInt(s) + 1, size);
                    }
                    Object[] obj = new Object[size];
                    for (Map.Entry k : ((Map<?, ?>) array).entrySet()) {
                        String s = String.valueOf(k.getKey()).split("\\?")[0];
                        int index = Integer.parseInt(s);
                        obj[index] = k.getValue();
                    }
                    return Arrays.asList(obj);
                }
                String s = array instanceof DataWrapper ? array.toString() : (String) array;
                byte[] c = s.getBytes(Charset.forName("windows-1251"));
                List lst = new ArrayList();
                for (byte b : c) {
                    lst.add(b & 0xff);
                }
                return lst;
            }

            @Override
            public int onStartArray() throws IOException {
                List lst = convertArray(data.pop());
                data.push(lst);
                int size = lst.size();
                out.writeInt(Integer.reverseBytes(size));
                return size;
            }

            @Override
            public void beforeNextArrayField(int index) throws IOException {
                Object d = data.peek();
                Object n = ((List) d).get(index);
                data.push(n == null ? NULL : n);
            }

            @Override
            public void onFinishArray() throws IOException {
                data.pop();
            }

            @Override
            public void beforeNextStructField(String name) throws IOException {
                if (name.isEmpty()) {
                    data.push(NULL);
                    return;
                }
                Object d = data.peek();
                if (d == NULL) {
                    data.push(NULL);
                    return;
                }
                Object n = ((Map<?, ?>) d).get(name);
                data.push(n == null ? NULL : n);
            }

            @Override
            public void onFinishStruct() throws IOException {
                data.pop();
            }
        });
    }

    public interface Listener {
        Listener EMPY = new Listener() {
        };

        default boolean onOptionalCheck() throws IOException {
            return false;
        }

        default void onI8() throws IOException {
        }

        default void onI16() throws IOException {
        }

        default void onI32() throws IOException {
        }

        default void onI64() throws IOException {
        }

        default void onFixedData(byte[] toByteArray) throws IOException {
        }

        default int onStartArray() throws IOException {
            return 0;
        }

        default void beforeNextArrayField(int index) throws IOException {
        }

        default void afterNextArrayField(int index) throws IOException {
        }

        default void onFinishArray() throws IOException {
        }

        default void onStartStruct() throws IOException {
        }

        default void beforeNextStructField(String name) throws IOException {
        }

        default void afterNextStructField(String name) throws IOException {
        }

        default void onFinishStruct() throws IOException {
        }
    }

    public static void parse(String template, Map<String, String> structs, Listener listener) throws IOException {
        new Object() {
            int pos = 0;

            int next() {
                if (pos == template.length()) {
                    return -1;
                }
                return template.charAt(pos++);
            }

            void awaitFor(int c) {
                int n = next();
                if (n != c) {
                    throw new IllegalArgumentException("Template is incorrect: " + (char) n + " is not " + (char) c);
                }
            }

            <T> T closeTerm(T r, char c) {
                awaitFor(c);
                return r;
            }

            void parseValue(Listener listener) throws IOException {
                int n = next();
                switch (n) {
                    case '{':
                        parseStruct(listener);
                        return;
                    case '#':
                        parseHex(listener);
                        return;
                    case '[':
                        parseArray(listener);
                        return;
                    case '?':
                        parseOptional(listener);
                        return;
                }
                pos--;
                parseType(listener);
            }

            void parseOptional(Listener listener) throws IOException {
                boolean hasValue = listener.onOptionalCheck();
                parseValue(hasValue ? listener : Listener.EMPY);
            }

            void parseType(Listener listener) throws IOException {
                String name = parseName();
                switch (name) {
                    case "i8":
                        listener.onI8();
                        break;
                    case "i16":
                        listener.onI16();
                        break;
                    case "i32":
                        listener.onI32();
                        break;
                    case "i64":
                        listener.onI64();
                        break;
                    default:
                        if (structs.get(name) == null) {
                            throw new IllegalArgumentException("Unknown struct " + name + " in pos " + pos + " for template " + template);
                        }
                        FormatParser.parse(structs.get(name), structs, listener);
                        break;
                }
            }

            int hex(int c) {
                c -= '0';
                if (c < 9) {
                    return c;
                }
                return c - 'A' + '0' + 10;
            }

            void parseHex(Listener listener) throws IOException {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                for (; ; ) {
                    int n = next();
                    if ((n >= '0' && n <= '9') || (n >= 'A' && n <= 'F')) {
                        int n2 = next();
                        if ((n2 >= '0' && n2 <= '9') || (n2 >= 'A' && n2 <= 'F')) {
                            int b = hex(n) * 16 + hex(n2);
                            data.write(b);
                        } else {
                            throw new IllegalArgumentException("Illegal char in template at pos " + pos);
                        }
                    } else {
                        listener.onFixedData(data.toByteArray());
                        pos--;
                        return;
                    }
                }
            }

            void parseArray(Listener listener) throws IOException {
                int arraySize = listener.onStartArray();
                int p = pos;
                for (int i = 0; i < arraySize; i++) {
                    pos = p;
                    listener.beforeNextArrayField(i);
                    parseValue(listener);
                    listener.afterNextArrayField(i);
                }
                if (arraySize == 0) {
                    parseValue(Listener.EMPY);
                }
                awaitFor(']');
                listener.onFinishArray();
            }

            void parseStruct(Listener listener) throws IOException {
                listener.onStartStruct();
                for (; ; ) {
                    int n = next();
                    if (n == ' ') {
                        continue;
                    }
                    pos--;
                    if (n == '}') {
                        listener.onFinishStruct();
                        return;
                    }
                    String name = closeTerm(parseName(), ':');
                    listener.beforeNextStructField(name);
                    parseValue(listener);
                    listener.afterNextStructField(name);
                }
            }

            String parseName() {
                StringBuilder builder = new StringBuilder();
                for (; ; ) {
                    int n = next();
                    if (Character.isAlphabetic(n) || Character.isDigit(n)) {
                        builder.append((char) n);
                    } else {
                        pos--;
                        return builder.toString();
                    }
                }
            }

        }.parseValue(listener);
    }
}
