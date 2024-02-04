package com.github.artyomcool.lodinfra.data.dto;

import java.util.Map;

public class Format {
    public String template;
    public Map<String, ProcessorWrapper> processors;
    public Map<String, String> structs;
    public Map<String, Alias> aliases;
}
