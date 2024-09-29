package com.github.artyomcool.lodinfra.dateditor.grammar;

import com.github.artyomcool.lodinfra.Pack;
import com.github.artyomcool.lodinfra.dateditor.grammar.gen.StructsLexer;
import com.github.artyomcool.lodinfra.dateditor.grammar.gen.StructsParser;
import com.github.artyomcool.lodinfra.dateditor.ui.StructUi;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GrammarParser {

    public static void main(String[] args) throws InterruptedException, IOException {
        String text = Files.readString(Path.of("C:\\Users\\Raider\\Desktop\\shared\\git\\HotA_1\\Htd.h"));
        int beginIndex = text.indexOf("// meta:BEGIN") + "// meta:BEGIN".length();
        text = text.substring(beginIndex, text.indexOf("// meta:END"));
        CodePointCharStream input = CharStreams.fromString(text);
        StructsLexer lexer = new StructsLexer(input);
        CommonTokenStream stream = new CommonTokenStream(lexer);
        //stream.seek(beginIndex);
        StructsParser parser = new StructsParser(stream);

        StructData root = new StructData(new NameTree(), null, "@root");
        for (StdTypes value : StdTypes.values()) {
            root.localDefinitions.put(value.type.name, value.type);
        }

        StructsParser.RootContext rootContext = parser.root();
        for (StructsParser.ElementContext ctx : rootContext.element()) {
            ScopedDefType defType = parseElement(stream, root, ctx);
            root.localDefinitions.put(defType.name, defType);
        }

        parser.reset();
        rootContext = parser.root();

        for (Map.Entry<String, DefType> entry : root.localDefinitions.entrySet()) {
            DefType value = entry.getValue();
            if (value instanceof StructData struct) {
                if (struct.meta.is("root")) {
                    Pack.run(new StructUi(struct));
                    return;
                }
            }
        }
    }

    private static NameTree getMeta(CommonTokenStream stream, ParserRuleContext ctx) {
        List<Token> left = stream.getHiddenTokensToLeft(ctx.getSourceInterval().a);
        NameTree meta = new NameTree();
        for (Token token : left) {
            if (token.getType() == StructsParser.LineComment) {
                String comment = token.getText();
                if (comment.startsWith("// meta:")) {
                    String metaString = comment.substring("// meta:".length());
                    String[] parts = metaString.split("\\s*=\\s*", 2);
                    String nameStr = parts[0];
                    meta.value(nameStr, parts.length > 1 ? parts[1] : "");
                }
            }
        }
        return meta;
    }

    private static ScopedDefType parseElement(CommonTokenStream stream, ScopedDefType parent, StructsParser.ElementContext ctx) {
        StructsParser.StructSpecifierContext structCtx = ctx.structSpecifier();
        StructsParser.EnumSpecifierContext enumCtx = ctx.enumSpecifier();
        if (structCtx != null) {
            return parseStruct(stream, parent, structCtx);
        } else if (enumCtx != null) {
            return parseEnum(stream, parent, enumCtx);
        } else {
            throw new RuntimeException("Strange state: " + ctx.start.getStartIndex() + "/" + ctx.stop.getStopIndex());
        }
    }

    private static StructData parseStruct(CommonTokenStream stream, ScopedDefType parent, StructsParser.StructSpecifierContext ctx) {
        TerminalNode node = ctx.Identifier();

        String name = node == null ? null : node.getText();
        StructData data = new StructData(getMeta(stream, ctx), parent, name);
        if (name != null) {
            parent.localDefinitions.put(name, data);
        }

        for (StructsParser.StructDeclarationContext declCtx : ctx.structDeclaration()) {
            StructsParser.TypeSpecifierContext typeCtx = declCtx.typeSpecifier();
            NameTree meta = getMeta(stream, declCtx);
            if (declCtx.element() != null) {
                parseElement(stream, data, declCtx.element());
            } else {
                data.declarations.add(getDeclaration(meta, parseDefType(data, typeCtx), declCtx));
            }
        }

        return data;
    }

    private static EnumData parseEnum(CommonTokenStream stream, ScopedDefType parent, StructsParser.EnumSpecifierContext ctx) {
        TerminalNode node = ctx.Identifier();

        String name = node == null ? null : node.getText();
        EnumData data = new EnumData(getMeta(stream, ctx), parent, name);
        if (name != null) {
            parent.localDefinitions.put(name, data);
        }

        for (StructsParser.EnumerationValueContext eCtx : ctx.enumerationValue()) {
            String enumName = eCtx.Identifier().getText();
            data.values.add(new EnumValue(enumName, getMeta(stream, eCtx)));
        }

        return data;
    }

    private static Declaration getDeclaration(NameTree meta, DefType type, StructsParser.StructDeclarationContext ctx) {
        String varName = ctx.Identifier().getText();
        StructsParser.InitializerContext initializer = ctx.initializer();
        if (type instanceof ScopedDefType st) {
            meta.parent = st.meta;
        }
        return new Declaration(meta, type, varName, "", initializer == null ? type.defaultInitializer() : initializer.getText());
    }

    private static DefType parseDefType(ScopedDefType parent, StructsParser.TypeSpecifierContext ctx) {
        if (ctx.generics() != null) {
            return parseGenerics(parent, ctx.generics());
        }
        return parent.findType(ctx.getText());
    }

    private static GenericType parseGenerics(ScopedDefType parent, StructsParser.GenericsContext ctx) {
        StdGenerics base = StdGenerics.valueOf(ctx.genericsTypeName().getText().toUpperCase());
        List<Object> types = new ArrayList<>();
        for (StructsParser.GenericParamContext paramContext : ctx.genericParam()) {
            if (paramContext.typeSpecifier() != null) {
                types.add(parseDefType(parent, paramContext.typeSpecifier()));
            } else {
                types.add(paramContext.initializer().getText());
            }
        }

        return new GenericType(parent, base, types);
    }

}
