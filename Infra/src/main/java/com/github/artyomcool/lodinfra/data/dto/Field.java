package com.github.artyomcool.lodinfra.data.dto;

import java.util.List;
import java.util.Map;

public class Field extends Group {
    public FieldType type;
    public String option;
    public String widthSample;
    public boolean fillWidth;
    public int maxWidth;
    public boolean breaks;
    public List<Value> values;
    public String id;
    public String dependencyField;
    public Map<String, Field> cases;
    public String link;
    public Map<String, Object> vars;
    public int min = Integer.MIN_VALUE;
    public int max = Integer.MAX_VALUE;
}
