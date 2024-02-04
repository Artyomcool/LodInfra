package com.github.artyomcool.lodinfra.data.dto;

import com.github.artyomcool.lodinfra.data.Helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {
    public List<TabGroup> tabs;
    public Map<String, Field> structs;
    public Map<String, List<Value>> enums;
    public Map<String, DynamicEnum> dynamicEnums;
    public Map<String, Format> formats;

    private transient Map<String, List<Value>> dynamicEnumsCache;

    public Map<String, List<Value>> dynamicEnums(Map<String, ?> toSerialize) {
        if (dynamicEnumsCache == null) {
            dynamicEnumsCache = new HashMap<>();
            for (Map.Entry<String, DynamicEnum> entry : dynamicEnums.entrySet()) {
                DynamicEnum value = entry.getValue();
                List data = (List) toSerialize.get(value.fromTab);
                List<Value> values = new ArrayList<>();
                for (Object d : data) {
                    Map<String, Object> params = new HashMap<>();
                    params.putAll((Map) d);
                    Value r = new Value();
                    for (Map.Entry<String, String> v : value.template.entrySet()) {
                        r.put(Helpers.evalString(v.getKey(), params), Helpers.evalString(v.getValue(), params));
                    }
                    values.add(r);
                }
                dynamicEnumsCache.put(entry.getKey(), values);
            }
        }
        return dynamicEnumsCache;
    }

    public void clearDynamicEnumCache() {
        this.dynamicEnumsCache = null;
    }
}
