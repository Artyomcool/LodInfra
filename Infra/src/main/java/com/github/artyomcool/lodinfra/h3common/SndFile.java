package com.github.artyomcool.lodinfra.h3common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SndFile implements Archive {

    public Path path;
    public List<SubFileMeta> files;
    public ByteBuffer originalData;

    @Override
    public Path originalPath() {
        return path;
    }

    @Override
    public List<? extends Element> files() {
        return files;
    }

    @Override
    public void writeHeader(ByteBuffer byteBuffer, int subFilesCount) {
        byteBuffer.putInt(subFilesCount);
    }

    public static class SubFileMeta implements Archive.Element {
        SndFile parent;
        String name;    // 40 bytes
        int offset;
        int size;

        @Override
        public Archive parent() {
            return parent;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ByteBuffer asByteBuffer() {
            return asOriginalByteBuffer();
        }

        @Override
        public ByteBuffer asOriginalByteBuffer() {
            return parent.originalData
                    .slice(offset, compressedSize() == 0 ? uncompressedSize() : compressedSize())
                    .asReadOnlyBuffer()
                    .order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
        public int fileType() {
            return 1;
        }

        @Override
        public int compressedSize() {
            return 0;
        }

        @Override
        public int uncompressedSize() {
            return size;
        }
    }

    public static Archive createEmpty(Path path) {
        SndFile file = new SndFile();
        file.files = new ArrayList<>();
        file.path = path;
        return file;
    }

    public static Archive load(Path file) throws IOException {
        return parse(file, ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN));
    }

    public static Archive parse(Path lodPath, ByteBuffer byteBuffer) {
        if (!byteBuffer.hasRemaining()) {
            return createEmpty(lodPath);
        }

        SndFile result = new SndFile();
        int count = byteBuffer.getInt();
        List<SubFileMeta> files = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] name = new byte[40];
            byteBuffer.get(name);

            SubFileMeta subFile = new SubFileMeta();
            subFile.parent = result;
            subFile.name = LodFile.nameFromBytes(name);
            subFile.offset = byteBuffer.getInt();
            subFile.size = byteBuffer.getInt();
            files.add(subFile);
        }

        result.originalData = byteBuffer;
        result.path = lodPath;
        result.files = files;
        return result;
    }

}
