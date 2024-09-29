package com.github.artyomcool.lodinfra.dateditor.grammar;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Util {

    public static String toHuman(String text) {
        return Arrays.stream(text.split("_"))
                .map(Util::capitalize)
                .collect(Collectors.joining(" "));
    }

    public static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

}
