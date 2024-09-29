package com.github.artyomcool.lodinfra.dateditor.grammar;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EnumData extends ScopedDefType {

    public final List<EnumValue> values = new ArrayList<>();

    public EnumData(NameTree meta, ScopedDefType type, String name) {
        super(meta, type, name);
    }

    @Override
    public String toString() {
        return "Enum" + header() + definitions() + "(" + values.stream().map(EnumValue::toString).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public RefStorage createDefault(DeclarationCtx declaration) {
        String value = declaration.declaration().defaultValue();
        return new RefStorage.Self(value.isEmpty() ? declaration.declaration().asEnum().values.get(0).name() : value);
    }
}
