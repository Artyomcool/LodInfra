package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;

public class Pcx extends DefInfo {

    public static final int TYPE8 = 0x10;
    public static final int TYPE24 = 0x11;

    public static DefInfo load(ByteBuffer buffer) {
        int size = buffer.getInt();
        int width = buffer.getInt();
        int height = buffer.getInt();

        if (size == width * height * 3) {
            return load24(buffer, width, height);
        }
        return load8(buffer, width, height);
    }

    public static DefInfo load24(ByteBuffer buffer, int width, int height) {
        int[][] result = new int[height][width];
        for (int y = 0; y < height; y++) {
            int[] scanline = result[y];
            for (int x = 0; x < width; x++) {
                int b = buffer.get() & 0xff;
                int g = buffer.get() & 0xff;
                int r = buffer.get() & 0xff;
                scanline[x] = 0xff000000 | r << 16 | g << 8 | b;
            }
        }
        DefInfo info = new DefInfo();
        info.type = TYPE24;
        info.fullWidth = width;
        info.fullHeight = height;

        Group group = new Group(info);
        info.groups.add(group);

        Frame frame = new Frame(group);
        group.frames.add(frame);

        frame.data = () -> result;

        return info;
    }

    public static DefInfo load8(ByteBuffer buffer, int width, int height) {
        int[][] result = new int[height][width];
        for (int y = 0; y < height; y ++) {
            int[] scanline = result[y];
            for (int x = 0; x < width; x++) {
                scanline[x] = buffer.get() & 0xff;
            }
        }

        int[] palette = new int[256];
        for (int i = 0; i < palette.length; i++) {
            int r = buffer.get() & 0xff;
            int g = buffer.get() & 0xff;
            int b = buffer.get() & 0xff;
            palette[i] = 0xff000000 | r << 16 | g << 8 | b;
        }
        for (int y = 0; y < height; y ++) {
            int[] scanline = result[y];
            for (int x = 0; x < width; x++) {
                scanline[x] = palette[scanline[x]];
            }
        }

        DefInfo info = new DefInfo();
        info.type = TYPE8;
        info.fullWidth = width;
        info.fullHeight = height;
        info.palette = palette;

        Group group = new Group(info);
        info.groups.add(group);

        Frame frame = new Frame(group);
        group.frames.add(frame);

        frame.data = () -> result;

        return info;
    }

}
