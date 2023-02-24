package com.github.artyomcool.lodinfra.h3common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class LodFile {

    public static final int MAGIC = 0x00444f4c;   // LOD\0 in little endian

    public static class SubFileMeta {
        public LodFile parent;
        public String nameAsString;
        public byte[] name = new byte[16];
        public int globalOffsetInFile;
        public int uncompressedSize;
        public int fileType;
        public int compressedSize;

        public ByteBuffer asByteBuffer() {
            if (compressedSize == 0) {
                return parent.originalData.slice(globalOffsetInFile, uncompressedSize).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
            }

            byte[] uncompressed = new byte[uncompressedSize];
            Inflater inflater = new Inflater();
            inflater.setInput(parent.originalData.slice(globalOffsetInFile, compressedSize));
            try {
                inflater.inflate(uncompressed);
            } catch (DataFormatException e) {
                throw new RuntimeException(e);
            }
            inflater.end();

            return ByteBuffer.wrap(uncompressed).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }

        public byte[] asBytes() throws DataFormatException {
            byte[] uncompressed = new byte[uncompressedSize];

            if (compressedSize == 0) {
                ByteBuffer slice = parent.originalData.slice(globalOffsetInFile, uncompressedSize);
                slice.get(uncompressed);
                return uncompressed;
            }

            Inflater inflater = new Inflater();
            inflater.setInput(parent.originalData.slice(globalOffsetInFile, compressedSize));
            inflater.inflate(uncompressed);
            inflater.end();

            return uncompressed;
        }
    }

    public Path path;
    public ByteBuffer originalData;

    public int magic;
    public int fileUseFlag;
    public int subFilesCount;
    public byte[] junk = new byte[80];
    public List<SubFileMeta> subFiles;

    public static LodFile createEmpty(Path path) {
        LodFile file = new LodFile();
        file.path = path;
        file.magic = MAGIC;
        file.fileUseFlag = 200;
        file.subFilesCount = 0;
        file.subFiles = new ArrayList<>();
        return file;
    }

    public static LodFile load(Path file) throws IOException {
        return parse(file, ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN));
    }

    public static LodFile loadOrCreate(Path lodPath) throws IOException {
        return Files.exists(lodPath) ? load(lodPath) : createEmpty(lodPath);
    }

    public static LodFile parse(Path lodPath, ByteBuffer byteBuffer) throws IOException {
        if (!byteBuffer.hasRemaining()) {
            return createEmpty(lodPath);
        }
        LodFile result = new LodFile();
        result.path = lodPath;
        result.originalData = byteBuffer;

        result.magic = byteBuffer.getInt();   // signature written in big endian
        if (result.magic != MAGIC) {
            throw new IOException("File is broken: magic is " + Integer.reverseBytes(result.magic));
        }
        result.fileUseFlag = byteBuffer.getInt();
        result.subFilesCount = byteBuffer.getInt();
        byteBuffer.get(result.junk);

        result.subFiles = new ArrayList<>(result.subFilesCount);
        for (int i = 0; i < result.subFilesCount; i++) {
            SubFileMeta meta = new SubFileMeta();
            meta.parent = result;

            byteBuffer.get(meta.name);
            meta.nameAsString = new String(meta.name, 0, indexOfZero(meta.name));
            meta.globalOffsetInFile = byteBuffer.getInt();
            meta.uncompressedSize = byteBuffer.getInt();
            meta.fileType = byteBuffer.getInt();
            meta.compressedSize = byteBuffer.getInt();

            result.subFiles.add(meta);
        }

        return result;
    }

    public static SubFileMeta findFirst(List<LodFile> files, String name) {
        for (LodFile file : files) {
            for (SubFileMeta subFile : file.subFiles) {
                if (subFile.nameAsString.equalsIgnoreCase(name)) {
                    return subFile;
                }
            }
        }
        return null;
    }

    private static int indexOfZero(byte[] name) {
        for (int i = 0; i < name.length; i++) {
            if (name[i] == 0) {
                return i;
            }
        }
        return name.length;
    }

}
