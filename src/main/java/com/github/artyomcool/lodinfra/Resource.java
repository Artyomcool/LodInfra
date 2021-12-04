package com.github.artyomcool.lodinfra;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;

public class Resource {

    public final int type;
    public final String lang;
    public final String name;
    public final String virtualPath;
    public final ByteBuffer data;
    public final int uncompressedSize;

    private Resource(int type, String lang, String name, String virtualPath, ByteBuffer data, int uncompressedSize) {
        this.type = type;
        this.lang = lang;
        this.name = name;
        this.virtualPath = virtualPath;
        this.data = data;
        this.uncompressedSize = uncompressedSize;
    }

    public static Resource fromPath(String lang, Path path) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(path));
        String name = path.getFileName().toString();
        return new Resource(
                typeOf(name, buffer),
                lang,
                name,
                path.toString(),
                buffer,
                0
        );
    }

    public static Resource fromString(Path holderPath, String lang, String name, String data) {
        byte[] dataBytes = data.getBytes(Utils.cp1251);
        return new Resource(
                2,
                lang,
                name,
                holderPath + ":" + name,
                ByteBuffer.wrap(dataBytes),
                0
        );
    }

    public static Resource fromLod(Path holderPath, LodFile file, LodFile.SubFileMeta meta) {
        String name = new String(meta.name).trim();
        try {
            return new Resource(
                    meta.fileType,
                    null,
                    name,
                    holderPath + ":" + name,
                    ByteBuffer.wrap(
                            file.originalData,
                            meta.globalOffsetInFile,
                            meta.compressedSize == 0 ? meta.uncompressedSize : meta.compressedSize
                    ),
                    meta.compressedSize == 0 ? 0 : meta.uncompressedSize
            );
        } catch (IndexOutOfBoundsException e) {
            throw new IndexOutOfBoundsException("Can't create resource: " + name + "/" + meta.globalOffsetInFile + "/" + file.originalData.length +"/" + meta.compressedSize +"/" + meta.uncompressedSize);
        }
    }

    public static Resource compress(Resource resource, Deflater deflater) {
        if (resource.uncompressedSize != 0) {
            return resource;
        }

        deflater.reset();
        deflater.setInput(resource.data.asReadOnlyBuffer());
        deflater.finish();

        ByteBuffer temp = ByteBuffer.allocate(resource.data.capacity());
        int compressedSize = deflater.deflate(temp, Deflater.FULL_FLUSH);
        temp.flip();

        if (compressedSize >= resource.data.remaining()) {
            return resource;
        } else {
            ByteBuffer clone = ByteBuffer.allocate(temp.remaining());
            clone.put(temp);
            clone.flip();

            return new Resource(
                    resource.type,
                    resource.lang,
                    resource.name,
                    resource.virtualPath + "#compressed",
                    clone,
                    resource.data.remaining()
            );
        }
    }

    private static int typeOf(String name, ByteBuffer data) {
        ByteBuffer buf;
        switch (name.split("\\.")[1].toLowerCase()) {
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
                buf = data.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
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
        throw new IllegalArgumentException("Unknown type for " + name);
    }

}
