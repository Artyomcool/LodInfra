package com.github.artyomcool.lodinfra.data.dto;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DataEntry extends LinkedHashMap<String, Object> {

    public final transient Set<String> dirty = new HashSet<>();

    public DataEntry() {
    }

    public DataEntry(Map<? extends String, ?> m) {
        super(m);
    }
}
