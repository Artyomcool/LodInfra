package com.github.artyomcool.lodinfra;

import org.apache.commons.text.diff.*;

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

        static Element change(StringBuilder from, StringBuilder to) {
            Element element = new Element();
            element.prev = new StringBuilder(from);
            element.now = new StringBuilder(to);
            return element;
        }

    }

    public static String diff(String oldString, String newString) {

        String endOld = "\0";
        String endNew = "\0";

        ReplacementsFinder finder = new ReplacementsFinder(oldString);
        new StringsComparator(oldString + endOld, newString + endNew).getScript().visit(finder);

        StringBuilder diff = new StringBuilder();
        Element last = null;
        for (Element change : finder.flushChanges()) {
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

    private static class ReplacementsFinder implements CommandVisitor<Character> {
        private final List<Element> changes = new ArrayList<>();

        private final StringBuilder pendingInsertions = new StringBuilder();
        private final StringBuilder pendingDeletions = new StringBuilder();

        private final String oldString;

        private int skipped;
        private int stringPos;

        private ReplacementsFinder(String oldString) {
            this.oldString = oldString;
        }

        @Override
        public void visitInsertCommand(Character object) {
            pendingInsertions.append(object);
        }
        @Override
        public void visitDeleteCommand(Character object) {
            pendingDeletions.append(object);
        }

        @Override
        public void visitKeepCommand(Character object) {
            if (pendingDeletions.length() == 0 && pendingInsertions.length() == 0) {
                ++skipped;
            } else {
                if (skipped != 0) {
                    changes.add(Element.same(oldString, stringPos, skipped));
                    stringPos += skipped;
                }

                changes.add(Element.change(pendingDeletions, pendingInsertions));
                stringPos += pendingDeletions.length();

                pendingDeletions.setLength(0);
                pendingInsertions.setLength(0);
                skipped = 1;
            }
        }

        public List<Element> flushChanges() {
            // there are always at least one same finishing synthetic character
            changes.add(Element.same(oldString, stringPos, skipped - 1));
            return changes;
        }

    }
}
