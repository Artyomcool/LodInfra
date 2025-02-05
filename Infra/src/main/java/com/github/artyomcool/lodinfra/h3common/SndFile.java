package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public class SndFile extends MediaFile {

    private SndFile(Path path, ByteBuffer byteBuffer) {
        super(path, byteBuffer);
    }

    public static Archive parse(Path lodPath, ByteBuffer byteBuffer) {
        return new SndFile(lodPath, byteBuffer);
    }

    @Override
    protected void postReadFubFile(SubFileMeta subFile, ByteBuffer byteBuffer) {
        subFile.size = byteBuffer.getInt();
    }
}
