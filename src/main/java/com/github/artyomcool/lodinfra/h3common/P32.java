package com.github.artyomcool.lodinfra.h3common;

import com.github.artyomcool.lodinfra.ui.ImgFilesUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

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

        IntBuffer pixels = IntBuffer.allocate(width * height);
        for (int yy = height - 1; yy >= 0; yy--) {
            for (int xx = 0; xx < width; xx++) {
                pixels.put(yy * width + xx, ImgFilesUtils.d32ToPcxColor(false, buffer.getInt()));
            }
        }

        ImgFilesUtils.premultiply(pixels.array());

        Frame frame = new Frame(group, def.fullWidth, def.fullHeight, pixels);
        frame.compression = 0;
        group.frames.add(frame);

        return def;
    }

    public static ByteBuffer pack(Frame frame) {
        int type = TYPE;
        int version = 0;
        int headerSize = 32;
        int fileSize = 40 + frame.fullWidth * frame.fullHeight;
        int imageOffset = 40;
        int imageSize = frame.fullWidth * frame.fullHeight;

        int width = frame.fullWidth;
        int height = frame.fullHeight;

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

        buffer.position(imageOffset);
        for (int j = height - 1; j >= 0; j--) {
            for (int i = 0; i < width; i++) {
                buffer.putInt(frame.color(i, j));
            }
        }

        return buffer.flip();
    }

}
