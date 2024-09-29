package com.github.artyomcool.lodinfra.dateditor.grammar;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StructData extends ScopedDefType {

    public final List<Declaration> declarations = new ArrayList<>();

    public StructData(NameTree meta, ScopedDefType type, String name) {
        super(meta, type, name);
    }

    @Override
    public String toString() {
        return "StructData" + header() + definitions() + "(" + declarations.stream().map(Declaration::toString).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public RefStorage createDefault(DeclarationCtx ctx) {
        RefStorage.Struct refStorage = new RefStorage.Struct(ctx.declaration().childrenCommentFormat());
        for (Declaration d : declarations) {
            refStorage.put(d.name(), d.createDefault());
        }
        return refStorage;
    }
}
