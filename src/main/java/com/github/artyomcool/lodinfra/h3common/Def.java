package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Def extends DefInfo {

    public static DefInfo load(ByteBuffer buffer) {
        DefInfo def = new DefInfo();

        def.type = buffer.getInt();
        def.fullWidth = buffer.getInt();
        def.fullHeight = buffer.getInt();

        int groupCount = buffer.getInt();
        int[] palette = new int[256];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = 0xff000000 | (buffer.get() & 0xff) << 16 | (buffer.get() & 0xff) << 8 | (buffer.get() & 0xff);
        }
        def.palette = palette;

        byte[] name = new byte[13];
        int[] offsets;

        for (int i = 0; i < groupCount; i++) {
            int groupIndex = buffer.getInt();
            int framesCount = buffer.getInt();
            buffer.getInt();    // 0
            buffer.getInt();    // 0

            String[] names = new String[framesCount];
            for (int j = 0; j < framesCount; j++) {
                buffer.get(name);
                try {
                    int q = 0;
                    while (name[q] != 0) {
                        q++;
                    }
                    names[j] = new String(name, 0, q);
                } catch (IndexOutOfBoundsException e) {
                    names[j] = new String(name);
                }
            }

            offsets = new int[framesCount];
            for (int j = 0; j < framesCount; j++) {
                offsets[j] = buffer.getInt();
            }

            DefInfo.Group group = new DefInfo.Group(def);
            group.groupIndex = groupIndex;

            for (int j = 0; j < framesCount; j++) {
                DefInfo.Frame frame = new DefInfo.Frame(group);
                int offset = offsets[j];
                frame.name = names[j];
                frame.compression = buffer.getInt(offset + 4);
                frame.data = () -> {
                    buffer.position(offset);

                    int size = buffer.getInt();
                    int compression = buffer.getInt();
                    int fullWidth = buffer.getInt();
                    int fullHeight = buffer.getInt();

                    int width = buffer.getInt();
                    int height = buffer.getInt();
                    int x = buffer.getInt();
                    int y = buffer.getInt();

                    int start = buffer.position();

                    int xx = x;
                    int yy = y;

                    int[][] image = new int[fullHeight][fullWidth];
                    for (int[] row : image) {
                        Arrays.fill(row, SPEC_COLORS[0]);
                    }

                    switch (compression) {
                        case 0 -> {
                            for (int i1 = 0; i1 < height; i1++) {
                                for (int j1 = 0; j1 < width; j1++) {
                                    image[y + i1][x + j1] = palette[buffer.get() & 0xff];
                                }
                            }
                        }
                        case 1 -> {
                            int[] offsets1 = new int[height];
                            for (int i1 = 0; i1 < offsets1.length; i1++) {
                                offsets1[i1] = buffer.getInt() + start;
                            }
                            for (int i1 : offsets1) {
                                buffer.position(i1);

                                for (int w = 0; w < width; ) {
                                    int index = (buffer.get() & 0xff);
                                    int count = (buffer.get() & 0xff) + 1;
                                    for (int j1 = 0; j1 < count; j1++) {
                                        image[yy][xx] = index == 0xff ? palette[buffer.get() & 0xff] : SPEC_COLORS[index];
                                        xx++;
                                    }
                                    w += count;
                                }
                                xx = x;
                                yy++;
                            }
                        }
                        case 2 -> {
                            int[] offsets1 = new int[height];
                            for (int i1 = 0; i1 < offsets1.length; i1++) {
                                offsets1[i1] = (buffer.getShort() & 0xffff) + start;
                            }
                            for (int i1 : offsets1) {
                                buffer.position(i1);

                                for (int w = 0; w < width; ) {
                                    int b = buffer.get() & 0xff;
                                    int index = b >> 5;
                                    int count = (b & 0x1f) + 1;
                                    for (int j1 = 0; j1 < count; j1++) {
                                        image[yy][xx] = index == 0x7 ? palette[buffer.get() & 0xff] : SPEC_COLORS[index];
                                        xx++;
                                        if (xx >= x + width) {
                                            yy++;
                                            xx = x;
                                        }
                                    }
                                    w += count;
                                }
                            }
                        }
                        case 3 -> {
                            int[] offsets1 = new int[width * height / 32];
                            for (int i1 = 0; i1 < offsets1.length; i1++) {
                                offsets1[i1] = (buffer.getShort() & 0xffff) + start;
                            }
                            for (int i1 : offsets1) {
                                buffer.position(i1);

                                int left = 32;
                                while (left > 0) {
                                    int b = buffer.get() & 0xff;
                                    int index = b >> 5;
                                    int count = (b & 0x1f) + 1;

                                    for (int j1 = 0; j1 < count; j1++) {
                                        image[yy][xx] = index == 0x7 ? palette[buffer.get() & 0xff] : SPEC_COLORS[index];
                                        xx++;
                                        if (xx >= x + width) {
                                            yy++;
                                            xx = x;
                                        }
                                    }

                                    left -= count;
                                }
                            }
                        }
                    }

                    return image;
                };
                group.frames.add(frame);
            }

            def.groups.add(group);
        }
        return def;
    }

    public static ByteBuffer pack(DefInfo def, Map<Frame, FrameInfo> links) {
        ByteBuffer buffer = ByteBuffer.allocate(100 * 1024 * 1024).order(ByteOrder.LITTLE_ENDIAN);

        int type = def.type;
        int fullWidth = def.fullWidth;
        int fullHeight = def.fullHeight;
        int groupCount = def.groups.size();
        buffer
                .putInt(type)
                .putInt(fullWidth)
                .putInt(fullHeight)
                .putInt(groupCount);

        int[] palette = def.palette;
        for (int c : palette) {
            buffer.put((byte) (c >> 16));
            buffer.put((byte) (c >> 8));
            buffer.put((byte) c);
        }

        Map<Frame, Integer> offsetToPutOffset = new HashMap<>();

        for (Group group : def.groups) {
            int groupIndex = group.groupIndex;
            int framesCount = group.frames.size();
            int unk1 = 0;
            int unk2 = 0;

            buffer
                    .putInt(groupIndex)
                    .putInt(framesCount)
                    .putInt(unk1)
                    .putInt(unk2);

            for (Frame f : group.frames) {
                putFrameName(buffer, links.get(f).name);
            }
            for (Frame f : group.frames) {
                offsetToPutOffset.put(f, buffer.position());
                buffer.putInt(0);   // offset
            }
        }

        Map<Integer, Byte> paletteMap = new HashMap<>();
        for (int i = 0; i < palette.length; i++) {
            paletteMap.put(palette[i], (byte) i);
        }

        for (FrameInfo frameInfo : links.values()) {
            for (Frame frame : frameInfo.frames) {
                buffer.putInt(offsetToPutOffset.get(frame), buffer.position());
            }
            PackedFrame packedFrame = frameInfo.packedFrame;

            int offsetOfSize = buffer.position();
            buffer.putInt(0);   // size
            buffer.putInt(packedFrame.compression);
            buffer.putInt(packedFrame.fullWidth);
            buffer.putInt(packedFrame.fullHeight);

            buffer.putInt(packedFrame.width);
            buffer.putInt(packedFrame.height);
            buffer.putInt(packedFrame.x);
            buffer.putInt(packedFrame.y);

            switch (packedFrame.compression) {
                case 0 -> {
                    for (int y = 0; y < packedFrame.height; y++) {
                        for (int x = 0; x < packedFrame.width; x++) {
                            buffer.put(paletteMap.get(packedFrame.data[y + packedFrame.y][x + packedFrame.x]));
                        }
                    }
                }
                case 1 -> {
                    Map<HashArray, Integer> offsets = new HashMap<>();
                    int offsetsPos = buffer.position();

                    buffer.position(offsetsPos + packedFrame.height * 4);
                    for (int y = 0; y < packedFrame.height; y++) {
                        int[] scanline = packedFrame.data[packedFrame.y + y];
                        int offset = offsets.computeIfAbsent(new HashArray(scanline), k -> buffer.position());
                        if (offset == buffer.position()) {
                            for (int x = 0; x < packedFrame.width; ) {
                                int color = scanline[x + packedFrame.x];
                                int index = paletteMap.get(color) & 0xff;
                                if (index < 8) {
                                    int count = 1;
                                    int xx = x + 1;
                                    while (xx < packedFrame.width && count < 256 && scanline[xx + packedFrame.x] == color) {
                                        count++;
                                        xx++;
                                    }
                                    buffer.put((byte) index);
                                    buffer.put((byte) (count - 1));
                                    x = xx;
                                } else {
                                    int count = 1;
                                    int xx = x;
                                    while (xx + 1 < packedFrame.width && count < 256 && paletteMap.get(scanline[xx + 1 + packedFrame.x]) < 8) {
                                        count++;
                                        xx++;
                                    }

                                    buffer.put((byte) 0xff);
                                    buffer.put((byte) (count - 1));
                                    while (x <= xx) {
                                        buffer.put(paletteMap.get(scanline[x + packedFrame.x]));
                                        x++;
                                    }
                                }
                            }
                        }
                        buffer.putInt(offsetsPos + y * 4, offset - offsetsPos);
                    }
                }
                case 2 -> {
                    Map<HashArray, Integer> offsets = new HashMap<>();
                    int offsetsPos = buffer.position();

                    buffer.position(offsetsPos + packedFrame.height * 2);
                    for (int y = 0; y < packedFrame.height; y++) {
                        int[] scanline = packedFrame.data[packedFrame.y + y];
                        int offset = offsets.computeIfAbsent(new HashArray(scanline), k -> buffer.position());
                        if (offset == buffer.position()) {
                            for (int x = 0; x < packedFrame.width; ) {
                                int color = scanline[x + packedFrame.x];
                                int index = paletteMap.get(color) & 0xff;
                                if (index < 6) {
                                    int count = 1;
                                    int xx = x + 1;
                                    while (xx < packedFrame.width && count < 32 && scanline[xx] == color) {
                                        count++;
                                        xx++;
                                    }
                                    buffer.put((byte) (index << 5 | (count - 1)));
                                    x = xx;
                                } else {
                                    int count = 1;
                                    int xx = x;
                                    while (xx + 1 < packedFrame.width && count < 32 && paletteMap.get(scanline[xx + 1]) < 6) {
                                        count++;
                                        xx++;
                                    }

                                    buffer.put((byte) (7 << 5 | (count - 1)));
                                    while (x <= xx) {
                                        buffer.put(paletteMap.get(scanline[x++]));
                                    }
                                }
                            }
                        }
                        buffer.putShort(offsetsPos + y * 2, (short) (offset - offsetsPos));
                    }
                }
                case 3 -> {
                    int blocksCount = packedFrame.width * packedFrame.height / 32;
                    Map<HashArray, Integer> offsets = new HashMap<>();
                    int offsetsPos = buffer.position();

                    buffer.position(offsetsPos + blocksCount * 2);

                    for (int y = 0; y < packedFrame.height; y += 32) {
                        for (int xw = 0; xw < packedFrame.width; xw += 32) {
                            int[] scanline = new int[32 * 32];
                            for (int yy = 0; yy < 32; yy++) {
                                System.arraycopy(packedFrame.data[packedFrame.y + y + yy], packedFrame.x + xw, scanline, yy * 32, 32);
                            }
                            int offset = offsets.computeIfAbsent(new HashArray(scanline), k -> buffer.position());
                            if (offset == buffer.position()) {
                                for (int x = 0; x < 32; ) {
                                    int color = scanline[x + packedFrame.x];
                                    int index = paletteMap.get(color) & 0xff;
                                    if (index < 6) {
                                        int count = 1;
                                        int xx = x + 1;
                                        while (xx < scanline.length && count < 32 && scanline[xx] == color) {
                                            count++;
                                            xx++;
                                        }
                                        buffer.put((byte) (index << 5 | (count - 1)));
                                        x = xx;
                                    } else {
                                        int count = 1;
                                        int xx = x;
                                        while (xx + 1 < scanline.length && count < 32 && paletteMap.get(scanline[xx + 1]) < 6) {
                                            count++;
                                            xx++;
                                        }

                                        buffer.put((byte) (7 << 5 | (count - 1)));
                                        while (x <= xx) {
                                            buffer.put(paletteMap.get(scanline[x++]));
                                        }
                                    }
                                }
                            }
                            buffer.putShort(offsetsPos + y * 2, (short) (offset - offsetsPos));
                        }
                    }
                }
            }

            buffer.putInt(offsetOfSize, buffer.position() - offsetOfSize);
        }
        return ByteBuffer.wrap(Arrays.copyOf(buffer.array(), buffer.position()));
    }

}
