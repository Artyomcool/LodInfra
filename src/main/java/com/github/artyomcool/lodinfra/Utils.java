package com.github.artyomcool.lodinfra;

import java.nio.charset.Charset;
import java.nio.file.Path;

class Utils {

    static final Charset cp1251 = Charset.forName("cp1251");

    public static Path resolveTemplate(String pattern, String lang, String lod) {
        String path = pattern.replace("#lang#", lang).replace("#lod#", lod);
        return Path.of(path + ".lod");
    }
}
