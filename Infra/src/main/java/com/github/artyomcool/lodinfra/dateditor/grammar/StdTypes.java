package com.github.artyomcool.lodinfra.dateditor.grammar;

public enum StdTypes {
    _BOOL8_,
    _INT16_,
    _INT32_,
    _FLOAT_,
    STR,
    TRANSLATED,
    TRANSLATED_TEXT;

    public final StdType type = new StdType(this);
}
