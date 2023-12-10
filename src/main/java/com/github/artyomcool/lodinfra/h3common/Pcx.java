package com.github.artyomcool.lodinfra.h3common;

import com.github.artyomcool.lodinfra.ui.ImgFilesUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

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
        IntBuffer pixels = IntBuffer.allocate(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int b = buffer.get() & 0xff;
                int g = buffer.get() & 0xff;
                int r = buffer.get() & 0xff;
                pixels.put(0xff000000 | r << 16 | g << 8 | b);
            }
        }
        DefInfo info = new DefInfo();
        info.type = TYPE24;
        info.fullWidth = width;
        info.fullHeight = height;

        Group group = new Group(info);
        info.groups.add(group);

        Frame frame = new Frame(group, width, height, pixels.flip());
        group.frames.add(frame);

        return info;
    }

    public static DefInfo load8(ByteBuffer buffer, int width, int height) {
        IntBuffer pixels = IntBuffer.allocate(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels.put(buffer.get() & 0xff);
            }
        }

        int[] palette = new int[256];
        for (int i = 0; i < palette.length; i++) {
            int r = buffer.get() & 0xff;
            int g = buffer.get() & 0xff;
            int b = buffer.get() & 0xff;
            palette[i] = 0xff000000 | r << 16 | g << 8 | b;
        }

        int[] array = pixels.array();
        for (int i = 0; i < array.length; i++) {
            array[i] = palette[array[i]];
        }

        DefInfo info = new DefInfo();
        info.type = TYPE8;
        info.fullWidth = width;
        info.fullHeight = height;
        info.palette = palette;

        Group group = new Group(info);
        info.groups.add(group);

        group.frames.add(new Frame(group, width, height, pixels.flip()));

        return info;
    }

    public static ByteBuffer pack(Frame frame) {
        int[] palette = frame.group.def.palette;
        if (palette != null) {
            int size = frame.fullWidth * frame.fullHeight;
            ByteBuffer buffer = ByteBuffer.allocate(12 + 256 + size)
                    .order(ByteOrder.LITTLE_ENDIAN);
            buffer
                    .putInt(size)
                    .putInt(frame.fullWidth)
                    .putInt(frame.fullHeight);

            Map<Integer, Byte> paletteIndex = new HashMap<>();
            for (int i = 0; i < palette.length; i++) {
                int c = palette[i];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;

                buffer
                        .put((byte) r)
                        .put((byte) g)
                        .put((byte) b);

                paletteIndex.put(c, (byte)i);
            }

            for (int y = 0; y < frame.fullHeight; y ++) {
                for (int x = 0; x < frame.fullWidth; x++) {
                    buffer.put(paletteIndex.get(frame.color(x, y)));
                }
            }
            return buffer.flip();
        } else {
            int size = frame.fullWidth * frame.fullHeight * 3;
            ByteBuffer buffer = ByteBuffer.allocate(12 + size)
                    .order(ByteOrder.LITTLE_ENDIAN);
            buffer
                    .putInt(size)
                    .putInt(frame.fullWidth)
                    .putInt(frame.fullHeight);

            for (int y = 0; y < frame.fullHeight; y ++) {
                for (int x = 0; x < frame.fullWidth; x++) {
                    int c = frame.color(x, y);
                    int r = (c >> 16) & 0xff;
                    int g = (c >> 8) & 0xff;
                    int b = c & 0xff;

                    buffer
                            .put((byte) r)
                            .put((byte) g)
                            .put((byte) b);
                }
            }
            return buffer.flip();
        }
    }

}
