package com.github.artyomcool.lodinfra.dateditor.grammar;

public record EnumValue(String name, NameTree meta) {
    public String toHuman() {
        return Util.toHuman(name);
    }
}
