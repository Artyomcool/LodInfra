package com.github.artyomcool.lodinfra;

import org.apache.commons.text.diff.EditScript;
import org.apache.commons.text.diff.ReplacementsFinder;
import org.apache.commons.text.diff.ReplacementsHandler;
import org.apache.commons.text.diff.StringsComparator;

import java.util.ArrayList;
import java.util.List;

public class StringDiffer {

    private static final int MIN_UNCHANGED_SEQUENCE = 5;

    private static class Element {
        StringBuilder prev;
        StringBuilder now;

        static Element same(String old, int pos, int count) {
            Element element = new Element();
            element.now = new StringBuilder(old.substring(pos, pos + count));
            return element;
        }

        static Element change(List<Character> from, List<Character> to) {
            Element element = new Element();
            element.prev = fromList(from);
            element.now = fromList(to);
            return element;
        }

        static StringBuilder fromList(List<Character> characters) {
            StringBuilder builder = new StringBuilder();
            characters.forEach(builder::append);
            return builder;
        }

    }

    public static String diff(String oldString, String newString) {
        List<Element> changes = new ArrayList<>();

        ReplacementsFinder<Character> finder = new ReplacementsFinder<>(new ReplacementsHandler<>() {
            int stringPos;
            @Override
            public void handleReplacement(int skipped, List<Character> from, List<Character> to) {
                if (skipped != 0) {
                    changes.add(Element.same(oldString, stringPos, skipped));
                    stringPos += skipped;
                }

                changes.add(Element.change(from, to));
                stringPos += from.size();
            }
        });
        new StringsComparator(oldString, newString).getScript().visit(finder);
        finder.visitKeepCommand(null);  // to flush all pending changes

        StringBuilder diff = new StringBuilder();
        Element last = null;
        for (Element change : changes) {
            if (last == null || last.prev == null) {
                append(diff, last);
                last = change;
                continue;
            }

            if (change.prev != null) {
                last.now.append(change.now);
                last.prev.append(change.prev);
                continue;
            }

            if (change.now.length() < MIN_UNCHANGED_SEQUENCE) {
                last.now.append(change.now);
                last.prev.append(change.now);
                continue;
            }

            append(diff, last);
            last = change;
        }

        append(diff, last);

        return diff.toString();
    }

    private static void append(StringBuilder diff, Element element) {
        if (element == null) {
            return;
        }

        if (element.prev == null) {
            append(diff, element.now);
            return;
        }

        append(diff, '[');
        if (element.prev.length() == 0) {
            append(diff, '+');
            append(diff, element.now);
        } else if (element.now.length() == 0) {
            append(diff, '-');
            append(diff, element.prev);
        } else {
            append(diff, element.prev);
            append(diff, "->");
            append(diff, element.now);
        }

        append(diff, ']');
    }

    private static void append(StringBuilder diff, CharSequence txt) {
        for (int i = 0; i < txt.length(); i++) {
            append(diff, txt.charAt(i));
        }
    }

    public static void append(StringBuilder diff, char c) {
        switch (c) {
            case '\0':
                return;
            case '\t':
                diff.append("\\t");
                return;
            case '\n':
                diff.append("\\n");
                return;
            default:
                diff.append(c);
        }
    }
}
