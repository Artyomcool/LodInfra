package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Msk {

    private final int width;
    private final int height;
    private long dirtyPixels;
    private long shadowPixels;

    public Msk(int width, int height) {
        this.width = width;
        this.height = height;
        this.dirtyPixels = 0;
        this.shadowPixels = 0;
    }

    public Msk(ByteBuffer buffer) {
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
    }

    public byte[] bytes() {
        byte[] result = new byte[14];
        result[0] = (byte) width;
        result[1] = (byte) height;
        result[2] = (byte) ((dirtyPixels >>> 40) & 0xff);
        result[3] = (byte) ((dirtyPixels >>> 32) & 0xff);
        result[4] = (byte) ((dirtyPixels >>> 24) & 0xff);
        result[5] = (byte) ((dirtyPixels >>> 16) & 0xff);
        result[6] = (byte) ((dirtyPixels >>> 8) & 0xff);
        result[7] = (byte) ((dirtyPixels >>> 0) & 0xff);
        result[8] = (byte) ((shadowPixels >>> 40) & 0xff);
        result[9] = (byte) ((shadowPixels >>> 32) & 0xff);
        result[10] = (byte) ((shadowPixels >>> 24) & 0xff);
        result[11] = (byte) ((shadowPixels >>> 16) & 0xff);
        result[12] = (byte) ((shadowPixels >>> 8) & 0xff);
        result[13] = (byte) ((shadowPixels >>> 0) & 0xff);
        return result;
    }

    public void markIsDirty(int x, int y) {
        dirtyPixels = mark(x, y, dirtyPixels);
    }

    public void markIsShadow(int x, int y) {
        shadowPixels = mark(x, y, shadowPixels);
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
        return (lines & (1L << delta)) != 0;
    }

    private long mark(int x, int y, long lines) {
        // y = 0..5
        // x = 0..7
        int delta = (height - y - 1) << 3 | (8 - width + x);
        return lines | (1L << delta);
    }
}
