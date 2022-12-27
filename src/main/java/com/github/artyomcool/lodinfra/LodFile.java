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

    public static class SubFileMeta {
        public byte[] name = new byte[16];
        public int globalOffsetInFile;
        public int uncompressedSize;
        public int fileType;
        public int compressedSize;
    }

    public ByteBuffer originalData;

    public int magic;
    public int fileUseFlag;
    public int subFilesCount;
    public byte[] junk = new byte[80];
    public List<SubFileMeta> subFiles;

    public static LodFile createEmpty() {
        LodFile file = new LodFile();
        file.magic = MAGIC;
        file.fileUseFlag = 200;
        file.subFilesCount = 0;
        file.subFiles = new ArrayList<>();
        return file;
    }

    public static LodFile load(Path file) throws IOException {
        return parse(ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN));
    }

    public static LodFile loadOrCreate(Path lodPath) throws IOException {
        return Files.exists(lodPath) ? load(lodPath) : createEmpty();
    }

    public static LodFile parse(ByteBuffer byteBuffer) throws IOException {
        LodFile result = new LodFile();
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
