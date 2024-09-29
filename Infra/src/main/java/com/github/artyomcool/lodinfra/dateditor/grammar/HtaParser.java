package com.github.artyomcool.lodinfra.dateditor.grammar;

public class HtaParser {

    private final String text;
    private int lineStartPos = 0;
    private int pos = 0;
    private int line = 0;
    private int col = 0;
    private int indent = 0;

    public HtaParser(String text) {
        this.text = text;
    }
    
    public RefStorage load() {
        return parseValue();
    }

    private RefStorage.Struct parseStructure() {
        if (text.charAt(pos) != '{') {
            throw new IllegalArgumentException("'{' character is expected");
        }

        RefStorage.Struct result = new RefStorage.Struct(null);

        pos++;
        newLine();
        indent++;
        while (skipIndentAndComment('}')) {

            int namePos = namePos();

            String name = text.substring(pos, namePos);
            pos = namePos + 1;

            result.put(name, parseValue());
        }
        pos++;
        newLine();
        indent--;

        return result;
    }

    private RefStorage parseList() {
        if (text.charAt(pos) != '[') {
            throw error("'[' character is expected");
        }

        RefStorage.List result = new RefStorage.List(null);

        pos++;
        newLine();
        indent++;
        while (skipIndentAndComment(']')) {
            result.add(parseValue());
        }
        pos++;
        newLine();
        indent--;

        return result;
    }

    private RefStorage parseValue() {
        return switch (text.charAt(pos)) {
            case '{' -> parseStructure();
            case '[' -> parseList();
            default -> parseString();
        };
    }

    private RefStorage parseString() {
        if (text.startsWith("'''", pos)) {
            return parseQuoted();
        }
        int lineEnd = text.indexOf('\n', pos);
        if (lineEnd == -1) {
            lineEnd = text.length();
        }
        int valEnd = lineEnd;
        if (valEnd != 0) {
            if (text.charAt(valEnd - 1) == '\r') {
                valEnd--;
            }
        }

        RefStorage.Self result = new RefStorage.Self(text.substring(pos, valEnd));
        pos = lineEnd + 1;
        markNewLine();

        return result;
    }

    private RefStorage parseQuoted() {
        if (!text.startsWith("'''", pos)) {
            throw error("''' is expected");
        }

        pos += "'''".length();
        newLine();

        int end = text.indexOf("'''", pos);
        int textEnd = end - 1;
        if (text.charAt(textEnd - 1) == '\r') {
            textEnd--;
        }

        RefStorage.Self result = new RefStorage.Self(text.substring(pos, textEnd));
        for (; pos < end; pos++) {
            if (text.charAt(pos) == '\n') {
                markNewLine();
            }
        }
        pos += "'''".length();
        newLine();

        return result;
    }

    private void newLine() {
        if (text.length() <= pos) {
            return;
        }
        if (text.charAt(pos) == '\n') {
            pos++;
            markNewLine();
            return;
        }
        if (text.charAt(pos) == '\r') {
            if (text.charAt(pos + 1) == '\n') {
                pos += 2;
                markNewLine();
                return;
            }
        }
        throw error("Newline is expected");
    }

    private void markNewLine() {
        line++;
        lineStartPos = pos;
    }

    private int namePos() {
        return text.indexOf(':', pos);
    }

    private boolean skipIndentAndComment(char endOfElement) {
        while (true) {
            skipIndent(indent - 1);
            if (text.charAt(pos) == endOfElement){
                return false;
            }
            skipIndent(1);

            if (!text.startsWith("###", pos)) {
                return true;
            }

            int end = text.indexOf('\n', pos);
            if (end == -1) {
                throw error("Expected new line");
            }
            pos = end + 1;
            markNewLine();
        }
    }

    private void skipIndent(int indent) {
        for (int i = 0; i < indent * 2; i++) {
            if (text.charAt(pos) != ' ') {
                throw error("Expected indent");
            }
            pos++;
        }
    }

    private IllegalArgumentException error(String msg) {
        throw new IllegalArgumentException(msg + " at " + line + ":" + (pos - lineStartPos));
    }

}
