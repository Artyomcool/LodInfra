package com.github.artyomcool.lodinfra.dateditor.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class DynamicPath {

    public static final DynamicPath ROOT = new DynamicPath(List.of("root"), List.of());

    final List<String> staticSegments;
    final List<Supplier<String>> dynamicSegments;

    private DynamicPath(List<String> staticSegments, List<Supplier<String>> dynamicSegments) {
        this.staticSegments = Collections.unmodifiableList(staticSegments);
        this.dynamicSegments = Collections.unmodifiableList(dynamicSegments);
    }

    public DynamicPath append(String segment) {
        ArrayList<String> segments = new ArrayList<>(staticSegments);
        int size = segments.size();
        if (size == dynamicSegments.size()) {
            segments.add(segment);
        } else {
            int last = size - 1;
            segments.set(last, segments.get(last) + "." + segment);
        }

        return new DynamicPath(segments, dynamicSegments);
    }

    public DynamicPath append(Supplier<String> segment) {
        ArrayList<Supplier<String>> segments = new ArrayList<>(dynamicSegments);
        int size = segments.size();
        if (size == staticSegments.size()) {
            int last = size - 1;
            Supplier<String> lastSegment = segments.get(last);
            segments.set(last, () -> lastSegment.get() + "." + segment.get());
        } else {
            segments.add(segment);
        }
        return new DynamicPath(staticSegments, segments);
    }

    public String toStaticPath() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < dynamicSegments.size(); i++) {
            result
                    .append(staticSegments.get(i))
                    .append('.')
                    .append(dynamicSegments.get(i).get());
        }
        if (staticSegments.size() != dynamicSegments.size()) {
            if (!result.isEmpty()) {
                result.append('.');
            }
            result.append(staticSegments.get(staticSegments.size() - 1));
        }
        return result.toString();
    }

}
