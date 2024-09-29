package com.github.artyomcool.lodinfra.dateditor.grammar;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ScopedDefType extends DefType {

    public final Map<String, DefType> localDefinitions = new HashMap<>();
    public final NameTree meta;

    public ScopedDefType(NameTree meta, ScopedDefType parent, String name) {
        super(parent, name);
        this.meta = meta;
    }

    public DefType findType(String name) {
        DefType defType = localDefinitions.get(name);
        if (defType != null) {
            return defType;
        }
        if (parent == null) {
            throw new RuntimeException("Type " + name + " not found");
        }
        return parent.findType(name);
    }

    protected String header() {
        String name = this.name;
        if (parent == null) {
            return "[" + name + "]";
        }
        return "[" + name + ":" + parent.name + "]";
    }

    protected String definitions() {
        return "{" + localDefinitions.values().stream().map(DefType::toString).collect(Collectors.joining(", ")) + "}";
    }

    @Override
    public String toString() {
        return "ScopedDefType" + header() + definitions();
    }
}
