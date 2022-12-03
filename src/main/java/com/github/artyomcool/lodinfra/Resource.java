package com.github.artyomcool.lodinfra;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class Resource {

    public final int type;
    public final String lang;
    public final String name;
    public final String sanitizedName;
    public final String virtualPath;
    public final ByteBuffer data;
    public final int uncompressedSize;

    public Resource(int type, String lang, String name, String sanitizedName, String virtualPath, ByteBuffer data, int uncompressedSize) {
        this.type = type;
        this.lang = lang;
        this.name = name;
        this.sanitizedName = sanitizedName;
        this.virtualPath = virtualPath;
        this.data = data;
        this.uncompressedSize = uncompressedSize;
    }

    public static String resourceName(Path path) {
        String name = path.getFileName().toString();
        String[] nameWithExt = name.split("\\.");
        String ext = nameWithExt[1];
        name = nameWithExt[0].split("#")[0] + "." + ext;
        return sanitizeName(name);
    }

    // TODO move it out
    public static Resource fromPath(String lang, Path path) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(path));
        String name = path.getFileName().toString();
        String[] nameWithExt = name.split("\\.");
        if (nameWithExt[nameWithExt.length - 1].equalsIgnoreCase("png")) {
            buffer = ResourceConverter.fromPng(name, buffer);
        } else if (nameWithExt[nameWithExt.length - 1].equalsIgnoreCase("bmp")) {
            buffer = ResourceConverter.fromBMP(name, buffer);
        }
        String ext = nameWithExt[1];
        name = nameWithExt[0].split("#")[0] + "." + ext;
        return new Resource(
                typeOf(ext, buffer),
                lang,
                name,
                resourceName(path),
                path.toString(),
                buffer,
                0
        );
    }

    public static String sanitizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static Resource fromString(Path holderPath, String lang, String name, String data) {
        byte[] dataBytes = data.getBytes(Utils.cp1251);
        return new Resource(
                2,
                lang,
                name,
                sanitizeName(name),
                holderPath + ":" + name,
                ByteBuffer.wrap(dataBytes),
                0
        );
    }

    public static Resource fromLod(Path holderPath, LodFile file, LodFile.SubFileMeta meta) {
        String name = new String(meta.name).trim();
        return new Resource(
                meta.fileType,
                null,
                name,
                sanitizeName(name),
                holderPath + ":" + name,
                ByteBuffer.wrap(
                        file.originalData,
                        meta.globalOffsetInFile,
                        meta.compressedSize == 0 ? meta.uncompressedSize : meta.compressedSize
                ),
                meta.compressedSize == 0 ? 0 : meta.uncompressedSize
        );
    }


    private static int typeOf(String ext, ByteBuffer data) {
        ByteBuffer buf;
        switch (ext.toLowerCase()) {
            case "h3c":
            case "xmi":
            case "ifr":
            case "p32":
            case "d32":
                return 1;
            case "txt":
                return 2;
            case "fnt":
                return 80;
            case "msk":
                return 79;
            case "pal":
                return 96;
            case "pcx":
                buf = data.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
                int size = buf.getInt();
                int width = buf.getInt();
                int height = buf.getInt();
                if (width == 0 || height == 0) {
                    return data.remaining() > 0 ? 16 : 17;
                }
                return size == width * height ? 16 : 17;
            case "def":
                if (data.remaining() < 4) {
                    return 1;
                }
                buf = data.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
                return buf.getInt();
        }
        throw new IllegalArgumentException("Unknown type for " + ext);
    }
}
