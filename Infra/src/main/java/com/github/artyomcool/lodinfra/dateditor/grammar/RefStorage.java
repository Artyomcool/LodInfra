package com.github.artyomcool.lodinfra.dateditor.grammar;

import java.util.ArrayList;
import java.util.TreeMap;

public interface RefStorage {
    String INTENT = "  ";

    RefStorage EMPTY = new RefStorage() {
        @Override
        public String self() {
            return null;
        }

        @Override
        public RefStorage child(String name) {
            return empty();
        }
    };

    static RefStorage empty() {
        return EMPTY;
    }

    default String value(String path) {
        return childForPath(path).self();
    }

    default RefStorage childForPath(String path) {
        if (path.isEmpty()) {
            return this;
        }

        int index = path.indexOf('.');
        if (index == -1) {
            return child(path);
        }
        return child(path.substring(0, index)).childForPath(path.substring(index + 1));
    }

    default String self() {
        throw new UnsupportedOperationException();
    }

    default void write(StringBuilder out, int intent) {
        throw new UnsupportedOperationException();
    }

    default void self(String value) {
        throw new UnsupportedOperationException();
    }

    RefStorage child(String name);

    class Struct extends TreeMap<String, RefStorage> implements RefStorage {

        private final String childrenCommentFormat;

        public Struct(String childrenCommentFormat) {
            this.childrenCommentFormat = childrenCommentFormat;
        }

        @Override
        public void write(StringBuilder out, int intent) {
            int nextIntent = intent + 1;
            out.append("{\n");

            String intentFull = INTENT.repeat(nextIntent);
            forEach((k, v) -> {
                String comment = comment(k, v);
                if (comment != null) {
                    out.append(intentFull)
                            .append("### ")
                            .append(comment, 1, comment.length() - 1)
                            .append("\n");
                }
                out.append(intentFull);
                out.append(k).append(':');
                v.write(out, nextIntent);
                out.append("\n");
            });

            out.append(INTENT.repeat(intent));
            out.append("}");
        }

        @Override
        public RefStorage child(String name) {
            return getOrDefault(name, empty());
        }

        private String comment(String key, RefStorage value) {
            if (childrenCommentFormat == null) {
                return null;
            }
            return childrenCommentFormat.replace("${k}", String.valueOf(key));
        }
    }

    class List extends ArrayList<RefStorage> implements RefStorage {
        private final String childrenCommentFormat;

        public List(String childrenCommentFormat) {
            this.childrenCommentFormat = childrenCommentFormat;
        }

        @Override
        public void write(StringBuilder out, int intent) {
            int nextIntent = intent + 1;
            out.append("[\n");

            String intentFull = INTENT.repeat(nextIntent);
            for (int i = 0; i < size(); i++) {
                RefStorage v = get(i);
                String comment = comment(i, v);
                if (comment != null) {
                    out.append(intentFull)
                            .append("### ")
                            .append(comment, 1, comment.length() - 1)
                            .append("\n");
                }
                out.append(intentFull);
                v.write(out, nextIntent);
                out.append("\n");
            }

            out.append(INTENT.repeat(intent));
            out.append("]");
        }

        @Override
        public RefStorage child(String name) {
            int index = Integer.parseInt(name);
            return index < 0 || index >= size() ? empty() : get(index);
        }

        private String comment(int index, RefStorage value) {
            if (childrenCommentFormat == null) {
                return null;
            }
            return childrenCommentFormat.replace("${i}", String.valueOf(index));
        }
    }

    class Self implements RefStorage {
        private String self;
        public Self(String self) {
            this.self = self;
        }
        @Override
        public String self() {
            return self;
        }
        @Override
        public void self(String value) {
            self = value;
        }

        @Override
        public void write(StringBuilder out, int intent) {
            if (self == null) {
                throw new RuntimeException();
            }

            if (self.contains("\n") || self.contains("###")) {
                out.append("'''\n").append(self).append("\n'''");
            } else {
                out.append(self);
            }
        }

        @Override
        public RefStorage child(String name) {
            return empty();
        }
    }

}
