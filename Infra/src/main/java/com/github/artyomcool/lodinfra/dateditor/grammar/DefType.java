package com.github.artyomcool.lodinfra.dateditor.grammar;

public abstract class DefType {

    public final ScopedDefType parent;
    public final String name;

    public DefType(ScopedDefType parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    @Override
    public String toString() {
        if (parent == null) {
            return "DefType[" + name + "]";
        }
        return "DefType[" + name + ":" + parent + "]";
    }

    public abstract RefStorage createDefault(DeclarationCtx declaration);

    public String defaultInitializer() {
        return "";
    }
}
