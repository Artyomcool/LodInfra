package com.github.artyomcool.lodinfra.data.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataEntry extends LinkedHashMap<String, Object> {
    public DataEntry() {
    }

    public DataEntry(Map<? extends String, ?> m) {
        super(m);
    }
}
