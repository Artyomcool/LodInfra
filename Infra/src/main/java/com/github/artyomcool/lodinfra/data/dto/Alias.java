package com.github.artyomcool.lodinfra.data.dto;

import java.util.Map;

public class Alias {
    public String path;
    public Map<String, Filter> filters;
    public Map<String, Map<String, Object>> structs;
}
