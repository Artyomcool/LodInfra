package com.github.artyomcool.lodinfra.data.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class WriteProcessor {
    public Processor process;
    public Map<String, String> before = new LinkedHashMap<>();
    public Map<String, String> after = new LinkedHashMap<>();
}
