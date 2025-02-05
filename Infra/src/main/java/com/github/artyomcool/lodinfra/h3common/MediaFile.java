package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MediaFile implements Archive {

    public final Path path;
    public final List<SubFileMeta> files;
    public final ByteBuffer originalData;

    protected MediaFile(Path lodPath, ByteBuffer byteBuffer) {
        if (byteBuffer == null || !byteBuffer.hasRemaining()) {
            files = new ArrayList<>();
            path = lodPath;
            originalData = null;
            return;
        }

        int start = byteBuffer.position();
        int count = byteBuffer.getInt();
        List<SubFileMeta> files = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] name = new byte[40];
            byteBuffer.get(name);

            SubFileMeta subFile = new SubFileMeta();
            subFile.parent = this;
            subFile.name = LodFile.nameFromBytes(name);
            subFile.offset = byteBuffer.getInt();
            postReadFubFile(subFile, byteBuffer);
            files.add(subFile);
        }

        originalData = byteBuffer;
        path = lodPath;
        this.files = files;

        postReadFile(byteBuffer, byteBuffer.position() - start);
    }

    protected void postReadFubFile(SubFileMeta subFile, ByteBuffer byteBuffer) {
    }

    protected void postReadFile(ByteBuffer byteBuffer, int amountReadSoFar) {
    }

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
        MediaFile parent;
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
}
