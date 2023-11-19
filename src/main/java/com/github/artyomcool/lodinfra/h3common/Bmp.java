package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class Bmp extends DefInfo {

    public static DefInfo load(ByteBuffer data) {
        int magic = data.getShort() & 0xffff;
        if (magic != 0x4D42) {  // BM
            throw new IllegalArgumentException("Invalid signature");
        }
        int fileSize = data.getInt();
        data.getInt();  // reserved
        int dataOffset = data.getInt();
        int headerSize = data.getInt();
        if (headerSize != 40) {
            throw new IllegalArgumentException("Invalid bmp");
        }
        int width = data.getInt();
        int height = data.getInt();
        int planes = data.getShort() & 0xffff;
        int bpp = data.getShort() & 0xffff;
        if (bpp != 24 && bpp != 8) {
            throw new IllegalArgumentException("Invalid bitmap size");
        }
        int compression = data.getInt();
        if (compression != 0) {
            throw new IllegalArgumentException("Compressed bmp are not supported");
        }
        int imageSize = data.getInt();
        int dpiX = data.getInt();
        int dpiY = data.getInt();
        int colorsUsed = data.getInt();
        int colorsImportant = data.getInt();

        if (colorsUsed == 0 && bpp == 8) {
            colorsUsed = 256;
        }

        boolean topToBottom = height < 0;
        height = Math.abs(height);

        byte[] imageData = new byte[dataOffset - (14 + headerSize + 4 * colorsUsed)];
        data.get(imageData);


        int[][] img = new int[height][width];
        DefInfo def = new DefInfo();
        if (bpp == 24) {
            int lineSize = (width * 3 + 3) / 4 * 4;

            int y, end, inc;
            if (topToBottom) {
                y = 0;
                end = height;
                inc = 1;
            } else {
                y = height - 1;
                end = -1;
                inc = -1;
            }

            int position = data.position();
            for (int i = 0; y != end; y += inc, i++) {
                int[] scanline = img[y];
                data.position(position + lineSize * i);
                for (int x = 0; x < width; x++) {
                    int b = data.get() & 0xff;
                    int g = data.get() & 0xff;
                    int r = data.get() & 0xff;
                    scanline[x] = 0xff000000 | r << 16 | g << 8 | b;
                }
            }

            Group group = new Group(def);
            Frame frame = new Frame(group);
            group.frames.add(frame);
            frame.data = () -> img;

            def.type = Pcx.TYPE24;
            def.fullWidth = width;
            def.fullHeight = height;
            def.groups.add(group);
        } else {
            int[] palette = new int[colorsUsed];
            for (int i = 0; i < colorsUsed; i++) {
                int b = data.get() & 0xff;
                int g = data.get() & 0xff;
                int r = data.get() & 0xff;
                data.get();

                palette[i] = 0xff000000 | r << 16 | g << 8 | b;
            }

            int lineSize = (width * 8 + 31) / 32 * 4;

            int y, end, inc;
            if (topToBottom) {
                y = 0;
                end = height;
                inc = 1;
            } else {
                y = height - 1;
                end = -1;
                inc = -1;
            }

            int position = data.position();

            for (int i = 0; y != end; y += inc, i++) {
                int[] scanline = img[i];
                data.position(position + lineSize * y);
                for (int x = 0; x < width; x++) {
                    scanline[x] = palette[data.get() & 0xff];
                }
            }

            Group group = new Group(def);
            Frame frame = new Frame(group);
            group.frames.add(frame);
            frame.data = () -> img;

            def.type = Pcx.TYPE8;
            def.fullWidth = width;
            def.fullHeight = height;
            def.palette = palette;
            def.groups.add(group);
        }

        return def;
    }

    public static ByteBuffer pack(Frame frame) {
        int[][] image = frame.data.decodeFrame();
        int[] palette = frame.group.def.palette;

        int width = frame.group.def.fullWidth;
        int height = frame.group.def.fullHeight;
        if (palette == null) {
            int rowSize = (width * 3 + 3) / 4 * 4;  // 3 bytes per pixel in RGB888, round up to multiple of 4
            int imageSize = rowSize * height;

            int fileSize = 14 + 40 + imageSize;

            ByteBuffer byteBuffer = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);

            // BITMAPFILEHEADER
            byteBuffer.put((byte) 'B').put((byte) 'M');
            byteBuffer.putInt(fileSize);
            byteBuffer.putShort((short) 0);
            byteBuffer.putShort((short) 0);
            byteBuffer.putInt(14 + 40);

            // BITMAPINFOHEADER
            byteBuffer.putInt(40);
            byteBuffer.putInt(width);
            byteBuffer.putInt(height);
            byteBuffer.putShort((short) 1);
            byteBuffer.putShort((short) 24);
            byteBuffer.putInt(0);
            byteBuffer.putInt(imageSize);
            byteBuffer.putInt(72);
            byteBuffer.putInt(72);
            byteBuffer.putInt(0);
            byteBuffer.putInt(0);

            for (int y = height - 1; y >= 0; y--) {
                int x;
                for (x = 0; x < width; x++) {
                    int color = image[y][x];
                    byteBuffer.put((byte) (color >>> 0));
                    byteBuffer.put((byte) (color >>> 8));
                    byteBuffer.put((byte) (color >>> 16));
                }
                for (int i = x * 3; i < rowSize; i++) {
                    byteBuffer.put((byte) 0);
                }
            }
            return byteBuffer.flip();
        } else {
            int rowSize = (width + 3) / 4 * 4;  // 1 bytes per pixel in palette, round up to multiple of 4
            int imageSize = rowSize * height;

            int fileSize = 14 + 40 + imageSize;

            ByteBuffer byteBuffer = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);

            // BITMAPFILEHEADER
            byteBuffer.put((byte) 'B').put((byte) 'M');
            byteBuffer.putInt(fileSize);
            byteBuffer.putShort((short) 0);
            byteBuffer.putShort((short) 0);
            byteBuffer.putInt(14 + 40);

            // BITMAPINFOHEADER
            byteBuffer.putInt(40);
            byteBuffer.putInt(width);
            byteBuffer.putInt(height);
            byteBuffer.putShort((short) 1);
            byteBuffer.putShort((short) 8);
            byteBuffer.putInt(0);
            byteBuffer.putInt(imageSize);
            byteBuffer.putInt(72);
            byteBuffer.putInt(72);
            byteBuffer.putInt(0);
            byteBuffer.putInt(0);

            Map<Integer, Byte> paletteMap = new HashMap<>();
            for (int i = 0; i < palette.length; i++) {
                byteBuffer.putInt(Integer.reverseBytes(palette[i]));
                paletteMap.put(palette[i], (byte) i);
            }

            for (int y = height - 1; y >= 0; y--) {
                int x;
                for (x = 0; x < width; x++) {
                    int color = image[y][x];
                    byteBuffer.put(paletteMap.get(color));
                }
                for (int i = x * 3; i < rowSize; i++) {
                    byteBuffer.put((byte) 0);
                }
            }
            return byteBuffer.flip();
        }
    }

}
