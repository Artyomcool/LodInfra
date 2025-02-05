package com.github.artyomcool.lodinfra.h3common;

import com.github.artyomcool.lodinfra.LodType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class LodFile implements Archive {

    public static final int MAGIC = 0x00444f4c;   // LOD\0 in little endian

    public static class SubFileMeta implements Element {
        public LodFile parent;
        public String nameAsString;
        public byte[] name = new byte[16];
        public int globalOffsetInFile;
        public int uncompressedSize;
        public int fileType;
        public int compressedSize;

        public ByteBuffer asByteBuffer() {
            ByteBuffer buffer = asOriginalByteBuffer();
            if (compressedSize == 0) {
                return buffer;
            }

            byte[] uncompressed = new byte[uncompressedSize];
            Inflater inflater = new Inflater();
            inflater.setInput(buffer);
            try {
                inflater.inflate(uncompressed);
            } catch (DataFormatException e) {
                throw new RuntimeException(e);
            }
            inflater.end();

            return ByteBuffer.wrap(uncompressed).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }

        public ByteBuffer asOriginalByteBuffer() {
            return parent.originalData
                    .slice(globalOffsetInFile, compressedSize == 0 ? uncompressedSize : compressedSize)
                    .asReadOnlyBuffer()
                    .order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
        public Archive parent() {
            return parent;
        }

        @Override
        public String name() {
            return nameAsString;
        }

        @Override
        public int fileType() {
            return fileType;
        }

        @Override
        public int compressedSize() {
            return compressedSize;
        }

        @Override
        public int uncompressedSize() {
            return uncompressedSize;
        }
    }

    public Path path;
    public ByteBuffer originalData;

    public int magic;
    public int fileUseFlag;
    public int subFilesCount;
    public byte[] junk = new byte[80];
    public List<SubFileMeta> subFiles;

    @Override
    public Path originalPath() {
        return path;
    }

    @Override
    public List<SubFileMeta> files() {
        return subFiles;
    }

    @Override
    public void writeHeader(ByteBuffer byteBuffer, int subFilesCount) {
        byteBuffer.putInt(magic);
        byteBuffer.putInt(fileUseFlag);
        byteBuffer.putInt(subFilesCount);
        byteBuffer.put(junk);
    }

    public static LodFile createEmpty(Path path) {
        LodFile file = new LodFile();
        file.path = path;
        file.magic = MAGIC;
        file.fileUseFlag = 200;
        file.subFilesCount = 0;
        file.subFiles = new ArrayList<>();
        return file;
    }

    public static Archive load(Path file) throws IOException {
        return parse(file, ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN));
    }

    public static Archive loadOrCreate(Path lodPath) throws IOException {
        return Files.exists(lodPath) ? load(lodPath) : createEmpty(lodPath);
    }

    public static Archive parse(Path lodPath, ByteBuffer byteBuffer) throws IOException {
        LodType type = LodType.forPath(lodPath);
        switch (type) {
            case SND -> {
                return SndFile.parse(lodPath, byteBuffer);
            }
            case VID -> {
                return VidFile.parse(lodPath, byteBuffer);
            }
        }
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

    private static int indexOfZero(byte[] name) {
        for (int i = 0; i < name.length; i++) {
            if (name[i] == 0) {
                return i;
            }
        }
        return name.length;
    }

    public static String nameFromBytes(byte[] name) {
        return new String(name, 0, indexOfZero(name));
    }

}
