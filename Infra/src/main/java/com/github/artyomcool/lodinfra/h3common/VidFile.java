package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public class VidFile extends MediaFile {

    protected VidFile(Path lodPath, ByteBuffer byteBuffer) {
        super(lodPath, byteBuffer);
    }

    public static Archive parse(Path lodPath, ByteBuffer byteBuffer) {
        return new VidFile(lodPath, byteBuffer);
    }

    @Override
    protected void postReadFile(ByteBuffer byteBuffer, int limit) {
        for (int i = 0; i < files.size(); i++) {
            SubFileMeta subFileMeta = files.get(i);
            int offset = subFileMeta.offset;
            int nextOffset = i == files.size() - 1 ? limit : files.get(i + 1).offset;
            subFileMeta.size = nextOffset - offset;
        }
    }
}
