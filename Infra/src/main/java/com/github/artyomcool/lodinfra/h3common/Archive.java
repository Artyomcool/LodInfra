package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

public interface Archive {

    Path originalPath();
    List<? extends Element> files();
    void writeHeader(ByteBuffer byteBuffer, int subFilesCount, boolean signed);

    interface Element {
        Archive parent();
        String name();
        default byte[] asBytes() {
            ByteBuffer buffer = asByteBuffer();
            if (buffer.hasArray() && buffer.array().length == buffer.remaining()) {
                return buffer.array();
            }
            byte[] result = new byte[buffer.remaining()];
            buffer.get(result);
            return result;
        }
        ByteBuffer asByteBuffer();
        ByteBuffer asOriginalByteBuffer();

        int fileType();
        int compressedSize();
        int uncompressedSize();
    }

}
