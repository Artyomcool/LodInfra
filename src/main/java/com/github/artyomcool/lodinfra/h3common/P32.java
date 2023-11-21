package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class P32 extends Def {

    public static final int TYPE = 0x46323350; // P32F little endian

    public static DefInfo load(ByteBuffer buffer) {
        DefInfo def = new DefInfo();
        def.type = buffer.getInt();
        int version = buffer.getInt();
        int headerSize = buffer.getInt();
        int fileSize = buffer.getInt();
        int imageOffset = buffer.getInt();
        int imageSize = buffer.getInt();

        int width = buffer.getInt();
        int height = buffer.getInt();

        def.fullWidth = width;
        def.fullHeight = height;

        Group group = new Group(def);
        def.groups.add(group);

        Frame frame = new Frame(group, () -> {
            int[][] image = new int[height][width];
            buffer.position(imageOffset);
            for (int j = height - 1; j >= 0; j--) {
                for (int i = 0; i < width; i++) {
                    image[j][i] = buffer.getInt();
                }
            }
            return image;
        });
        frame.compression = 0;
        group.frames.add(frame);

        return def;
    }

    public static ByteBuffer pack(Frame frame) {
        DefInfo def = frame.group.def;

        int type = TYPE;
        int version = 0;
        int headerSize = 32;
        int fileSize = 40 + def.fullWidth * def.fullHeight;
        int imageOffset = 40;
        int imageSize = def.fullWidth * def.fullHeight;

        int width = def.fullWidth;
        int height = def.fullHeight;

        ByteBuffer buffer = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer
                .putInt(type)
                .putInt(version)
                .putInt(headerSize)
                .putInt(fileSize)
                .putInt(imageOffset)
                .putInt(imageSize)
                .putInt(width)
                .putInt(height);

        int[][] image = def.groups.get(0).frames.get(0).decodeFrame();
        buffer.position(imageOffset);
        for (int j = height - 1; j >= 0; j--) {
            int[] scanline = image[j];
            for (int i = 0; i < width; i++) {
                buffer.putInt(scanline[i]);
            }
        }

        return buffer.flip();
    }

}
