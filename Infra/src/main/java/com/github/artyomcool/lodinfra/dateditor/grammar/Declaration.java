package com.github.artyomcool.lodinfra.dateditor.grammar;

import java.util.List;

public record Declaration(NameTree meta, DefType type, String name, String suffix, String defaultValue) {
    public String textName() {
        String name = meta.value("name");
        if (name != null) {
            return name + suffix;
        }

        name = this.name;
        if (name != null) {
            return Util.toHuman(name) + suffix;
        }

        return type.name + suffix;
    }

    public StructData asStruct() {
        return (StructData) type;
    }

    public EnumData asEnum() {
        return (EnumData) type;
    }

    public StdType asStd() {
        return (StdType) type;
    }

    public GenericType asGeneric() {
        return (GenericType) type;
    }

    public List<Declaration> destructure() {
        return asStruct().declarations;
    }

    public RefStorage createDefault() {
        return type.createDefault(new DeclarationCtx(this));
    }

    public String childrenCommentFormat() {
        return meta.value("children.comment");
    }

    public Declaration extractElement() {
        DefType type = (DefType) asGeneric().params.get(0);
        NameTree meta = this.meta;
        if (type instanceof ScopedDefType s) {
            meta = meta.withParent(s.meta);
        }
        return new Declaration(meta, type, name, suffix, type.defaultInitializer());
    }

    public Declaration withMeta(NameTree meta) {
        return new Declaration(meta, type, name, suffix, defaultValue);
    }

}