package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Msk {

    private final int width;
    private final int height;
    private final long dirtyPixels;
    private final long shadowPixels;

    public Msk(ByteBuffer buffer) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        width = buffer.get() & 0xff;
        height = buffer.get() & 0xff;
        dirtyPixels = (buffer.get() & 0xffL) << 40
                | (buffer.get() & 0xffL) << 32
                | (buffer.get() & 0xffL) << 24
                | (buffer.get() & 0xffL) << 16
                | (buffer.get() & 0xffL) << 8
                | (buffer.get() & 0xffL);
        shadowPixels = (buffer.get() & 0xffL) << 40
                | (buffer.get() & 0xffL) << 32
                | (buffer.get() & 0xffL) << 24
                | (buffer.get() & 0xffL) << 16
                | (buffer.get() & 0xffL) << 8
                | (buffer.get() & 0xffL);
        System.out.println(Long.toHexString(dirtyPixels) + Long.toHexString(shadowPixels));
    }

    public boolean isDirty(int x, int y) {
        return hasBit(x, y, dirtyPixels);
    }

    public boolean isShadow(int x, int y) {
        return hasBit(x, y, shadowPixels);
    }

    private boolean hasBit(int x, int y, long lines) {
        // y = 0..5
        // x = 0..7
        int delta = (height - y - 1) << 3 | (8 - width + x);
        return ((lines >>> delta) & 1) == 1;
    }

}
