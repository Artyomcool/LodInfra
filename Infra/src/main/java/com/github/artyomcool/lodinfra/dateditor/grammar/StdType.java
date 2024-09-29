package com.github.artyomcool.lodinfra.dateditor.grammar;

public class StdType extends DefType {

    public final StdTypes stdType;

    public StdType(StdTypes stdTypes) {
        super(null, stdTypes.name().toLowerCase());
        this.stdType = stdTypes;
    }

    @Override
    public RefStorage createDefault(DeclarationCtx ctx) {
        return switch (stdType) {
            case STR -> str(ctx);
            case _BOOL8_ -> new RefStorage.Self(ctx.declaration().defaultValue().isEmpty() ? "false" : ctx.declaration().defaultValue().toLowerCase());
            case _INT16_, _INT32_ -> new RefStorage.Self(ctx.declaration().defaultValue());
            case TRANSLATED, TRANSLATED_TEXT -> new RefStorage.Self("");
            default -> throw new IllegalStateException("Unexpected value: " + stdType);
        };
    }

    private static RefStorage.Self str(DeclarationCtx ctx) {
        return new RefStorage.Self(unwrap(ctx.declaration().defaultValue()));
    }

    @Override
    public String defaultInitializer() {
        return switch (stdType) {
            case _INT32_ -> "0";
            default -> "";
        };
    }

    private static String unwrap(String str) {
        return str == null ? null : (str.startsWith("\"") ? str.substring(1, str.length() -1) : str);
    }
}
