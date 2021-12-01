package com.github.artyomcool.lodinfra;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LodFile {

    public static final int MAGIC = 0x00444f4c;   // LOD\0 in little endian

    static class SubFileMeta {
        byte[] name = new byte[16];
        int globalOffsetInFile;
        int uncompressedSize;
        int fileType;
        int compressedSize;
    }

    byte[] originalData;

    int magic;
    int fileUseFlag;
    int subFilesCount;
    byte[] junk = new byte[80];
    List<SubFileMeta> subFiles;

    public static LodFile createEmpty() {
        LodFile file = new LodFile();
        file.magic = MAGIC;
        file.fileUseFlag = 200;
        file.subFilesCount = 0;
        file.subFiles = new ArrayList<>();
        return file;
    }

    public static LodFile load(Path file) throws IOException {
        return parse(Files.readAllBytes(file));
    }

    public static LodFile loadOrCreate(Path lodPath) throws IOException {
        return Files.exists(lodPath) ? load(lodPath) : createEmpty();
    }

    public static LodFile parse(byte[] data) throws IOException {
        LodFile result = new LodFile();
        result.originalData = data;

        ByteBuffer byteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
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

            byteBuffer.get(meta.name);
            meta.globalOffsetInFile = byteBuffer.getInt();
            meta.uncompressedSize = byteBuffer.getInt();
            meta.fileType = byteBuffer.getInt();
            meta.compressedSize = byteBuffer.getInt();

            result.subFiles.add(meta);
        }

        return result;
    }

}
