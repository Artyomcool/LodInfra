package com.github.artyomcool.lodinfra.data.dto;

import java.util.LinkedHashMap;
import java.util.List;

public class Data extends LinkedHashMap<String, List<DataEntry>> {
    public transient boolean dirty;
}
