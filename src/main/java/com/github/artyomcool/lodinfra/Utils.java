package com.github.artyomcool.lodinfra;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class Utils {

    static final Charset cp1251 = Charset.forName("cp1251");

    public static Path resolveTemplate(Path self, String pattern, String lang, String lod) {
        String path = pattern.replace("#lang#", lang).replace("#lod#", lod);
        return self.resolve(path + ".lod");
    }

    public static void deleteDir(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return this.visitFile(dir, null);
            }
        });
    }
}
