package com.github.artyomcool.lodinfra.dateditor.ui;

import java.util.*;
import java.util.function.Supplier;

public class DynamicPathMap<V> {

    private record DynamicEntry<V>(Supplier<String> segmentSupplier, MapSegment<V> next) {
    }

    final static class MapSegment<V> {
        private final MapSegment<V> parent;
        private final Map<String, MapSegment<V>> staticSegments = new HashMap<>();
        private final List<DynamicEntry<V>> dynamicSegments = new ArrayList<>();
        private final List<V> values = new ArrayList<>();

        private MapSegment(MapSegment<V> parent) {
            this.parent = parent;
        }

        MapSegment<V> appendNext(String staticKey) {
            return staticSegments.computeIfAbsent(staticKey, s -> new MapSegment<>(this));
        }

        MapSegment<V> appendNext(Supplier<String> dynamicKey) {
            MapSegment<V> m = new MapSegment<>(this);
            dynamicSegments.add(new DynamicEntry<>(dynamicKey, m));
            return m;
        }

        MapSegment<V> appendWildcard() {
            MapSegment<V> m = new MapSegment<>(this);
            dynamicSegments.add(new DynamicEntry<>(null, m));
            return m;
        }

        public Runnable add(V value) {
            values.add(value);
            return () -> removeAndCleanup(value);
        }

        public void removeAndCleanup(V value) {
            values.remove(value);
            cleanup();
        }

        public void cleanup() {
            if (parent != null && values.isEmpty() && staticSegments.isEmpty() && dynamicSegments.isEmpty()) {
                parent.dynamicSegments.removeIf(e -> e.next == this);
                parent.staticSegments.values().removeIf(s -> s == this);
                parent.cleanup();
            }
        }
    }

    private final MapSegment<V> root = new MapSegment<>(null);
    private final Map<DynamicPath, MapSegment<V>> tails = new HashMap<>();

    public Runnable add(DynamicPath path, V value) {
        return tails.computeIfAbsent(path, dynamicPath -> {
            MapSegment<V> node = root;

            for (int i = 0; i < path.dynamicSegments.size(); i++) {
                node = append(node, path.staticSegments.get(i));
                node = node.appendNext(path.dynamicSegments.get(i));
            }

            if (path.staticSegments.size() > path.dynamicSegments.size()) {
                node = append(node, path.staticSegments.get(path.staticSegments.size() - 1));
            }
            return node;
        }).add(value);
    }

    public List<V> get(String key) {
        List<MapSegment<V>> currentWave = new ArrayList<>();
        currentWave.add(root);

        List<MapSegment<V>> nextWave = new ArrayList<>();

        String[] segments = key.split("\\.");
        for (String segment : segments) {
            for (MapSegment<V> node : currentWave) {
                if (segment.equals("*")) {
                    nextWave.addAll(node.staticSegments.values());
                    for (DynamicEntry<V> entry : node.dynamicSegments) {
                        nextWave.add(entry.next);
                    }
                } else {
                    MapSegment<V> staticSegment = node.staticSegments.get(segment);
                    if (staticSegment != null) {
                        nextWave.add(staticSegment);
                    }
                    for (DynamicEntry<V> entry : node.dynamicSegments) {
                        if (entry.segmentSupplier == null) {
                            nextWave.add(entry.next);
                        } else {
                            String subsegments = entry.segmentSupplier.get();
                            if (subsegments.equals("*") || segment.equals(subsegments)) {
                                nextWave.add(entry.next);
                            } else if (subsegments.startsWith(segment)
                                    && subsegments.length() > segment.length()
                                    && subsegments.charAt(segment.length()) == '.') {
                                nextWave.add(synthetic(entry.next.values, subsegments.substring(segment.length() +1)));
                            }
                        }
                    }
                }
            }
            currentWave.clear();

            var tmp = currentWave;
            currentWave = nextWave;
            nextWave = tmp;
        }

        List<V> result = new ArrayList<>();
        for (MapSegment<V> s : currentWave) {
            result.addAll(s.values);
        }

        return result;
    }

    private MapSegment<V> append(MapSegment<V> node, String segment) {
        for (String s : segment.split("\\.")) {
            node = s.equals("*") ? node.appendWildcard() : node.appendNext(s);
        }
        return node;
    }

    private MapSegment<V> synthetic(List<V> values, String newPath) {
        MapSegment<V> r = append(new MapSegment<>(null), newPath);
        r.values.addAll(values);
        return r;
    }

}
