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
    protected void postReadFile(ByteBuffer byteBuffer, int amountReadSoFar) {
        int lastOffset = amountReadSoFar;
        for (int i = files.size() - 1; i >= 0; i--) {
            SubFileMeta subFileMeta = files.get(i);
            int offset = subFileMeta.offset;
            subFileMeta.size = lastOffset - offset;

            lastOffset = offset;
        }
    }
}
