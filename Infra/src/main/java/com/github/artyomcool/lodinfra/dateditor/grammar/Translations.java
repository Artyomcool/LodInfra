package com.github.artyomcool.lodinfra.dateditor.grammar;

import java.util.Map;
import java.util.TreeMap;

public class Translations {

    public static Map<String, String> parse(String text) {
        Map<String, String> translations = new TreeMap<>();
        int index = 0;
        while (true) {
            int prevIndex = index;
            index = skipComment(text, index);
            if (index == -1) {
                break;
            }
            index = skipWhite(text, index);
            if (index == -1) {
                break;
            }
            index = skipSeparator(text, index);
            if (index == -1) {
                break;
            }
            if (index != prevIndex) {
                continue;
            }

            index = skipTillLineEnd(text, index);
            String name = text.substring(prevIndex, index);

            do {
                prevIndex = index;
                index = skipWhite(text, index);
                index = skipComment(text, index);
            } while (prevIndex != index);

            index = skipSeparator(text, index);
            do {
                prevIndex = index;
                index = skipComment(text, index);
            } while (prevIndex != index);

            index = findSeparator(text, index) - 1;
            if (text.charAt(index - 1) == '\r') {
                index--;
            }

            String value = text.substring(prevIndex, index);

            translations.put(name, value);
        }

        return translations;
    }

    private static int skipComment(String text, int index) {
        if (text.startsWith("#", index)) {
            index = text.indexOf('\n', index);
            if (index == -1) {
                return -1;
            }
            return index + 1;
        }
        return index;
    }

    private static int skipWhite(String text, int index) {
        if (index >= text.length()) {
            return -1;
        }
        int prev = index;
        while (text.charAt(index) <= ' ') {
            if (text.charAt(index) == '\n') {
                return index + 1;
            }
            index++;
            if (index >= text.length()) {
                return -1;
            }
        }
        return prev;
    }

    private static int skipSeparator(String text, int index) {
        String delimiter = "*=*=*=*=*";
        if (text.startsWith(delimiter, index)) {
            index += delimiter.length();
            return skipWhite(text, index);
        }
        return index;
    }

    private static int findSeparator(String text, int index) {
        String delimiter = "*=*=*=*=*";
        return text.indexOf(delimiter, index);
    }

    private static int skipTillLineEnd(String text, int index) {
        while (text.charAt(index) != '\n' && text.charAt(index) != '\r') {
            index++;
        }
        return index;
    }

}
