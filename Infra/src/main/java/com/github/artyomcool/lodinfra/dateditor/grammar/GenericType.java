package com.github.artyomcool.lodinfra.dateditor.grammar;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GenericType extends DefType {

    public final StdGenerics base;
    public final List<Object> params;

    public GenericType(ScopedDefType parent, StdGenerics base, List<Object> params) {
        super(parent, null);
        this.base = base;
        this.params = params;
    }

    @Override
    public String toString() {
        String p = params.stream().map(Object::toString).collect(Collectors.joining(", "));
        String parentName = parent == null ? "" : (":" + parent.name);
        return "GenericType[" + base.name().toLowerCase() + "<" + p + ">" + parentName + "]";
    }

    @Override
    public RefStorage createDefault(DeclarationCtx declaration) {
        return switch (base) {
            case ARRAY -> {
                RefStorage.List result = new RefStorage.List(declaration.declaration().childrenCommentFormat());
                DefType childType = (DefType) params.get(0);
                int size = Integer.parseInt((String) params.get(1));
                for (int i = 0; i < size; i++) {
                    result.add(childType.createDefault(declaration));
                }
                yield result;
            }
            case VECTOR -> new RefStorage.List(declaration.declaration().childrenCommentFormat());
            case UNORDERED_MAP, MAP -> new RefStorage.Struct(declaration.declaration().childrenCommentFormat());
            default -> throw new IllegalStateException("Unexpected value: " + base);
        };
    }
}
