package com.github.artyomcool.lodinfra;

import com.github.artyomcool.lodinfra.h3common.Archive;

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
        this.name = name.toLowerCase().endsWith(".wav") ? name.substring(0, name.lastIndexOf('.')) : name;
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

    public static Resource fromLod(Path holderPath, Archive.Element meta) {
        String name = meta.name();
        return new Resource(
                meta.fileType(),
                null,
                name,
                sanitizeName(name),
                holderPath + ":" + name,
                meta.asOriginalByteBuffer(),
                meta.compressedSize() == 0 ? 0 : meta.uncompressedSize()
        );
    }


    private static int typeOf(String ext, ByteBuffer data) {
        ByteBuffer buf;
        return switch (ext.toLowerCase()) {
            case "h3c", "xmi", "ifr", "p32", "d32", "wav" -> 1;
            case "txt" -> 2;
            case "fnt" -> 80;
            case "msk" -> 79;
            case "pal" -> 96;
            case "pcx" -> {
                buf = data.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
                int size = buf.getInt();
                int width = buf.getInt();
                int height = buf.getInt();
                if (width == 0 || height == 0) {
                    yield data.remaining() > 0 ? 16 : 17;
                }
                yield size == width * height ? 16 : 17;
            }
            case "def" -> {
                if (data.remaining() < 4) {
                    yield 1;
                }
                buf = data.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
                yield buf.getInt();
            }
            default -> throw new IllegalStateException("Unknown type for: " + ext);
        };
    }

    public static Path pathInLod(Path lod, String resourceName) {
        return lod.resolveSibling(lod.getFileName() + "=@=@=" + resourceName);
    }

    public static Path pathOfLod(Path resource) {
        String fileName = String.valueOf(resource.getFileName());
        int lodSuffixIndex = fileName.indexOf("=@=@=");
        if (lodSuffixIndex == -1) {
            return null;
        }
        return resource.resolveSibling(fileName.substring(0, lodSuffixIndex));
    }

    public static String fileNamePossibleInLod(Path resource) {
        String fileName = String.valueOf(resource.getFileName());
        int lodSuffixIndex = fileName.indexOf("=@=@=");
        if (lodSuffixIndex == -1) {
            return fileName;
        }
        return fileName.substring(lodSuffixIndex + "=@=@=".length());
    }

}
