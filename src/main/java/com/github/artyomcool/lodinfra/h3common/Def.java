package com.github.artyomcool.lodinfra.h3common;

import com.github.artyomcool.lodinfra.ui.Box;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
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
                int offset = offsets[j];

                ByteBuffer buf = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(offset);

                int size = buf.getInt();
                int compression = buf.getInt();
                int fullWidth = buf.getInt();
                int fullHeight = buf.getInt();

                int width = buf.getInt();
                int height = buf.getInt();
                int x = buf.getInt();
                int y = buf.getInt();

                int start = buf.position();

                IntBuffer pixels = IntBuffer.allocate(fullHeight * fullWidth);
                int[] pixelsArray = pixels.array();
                Arrays.fill(pixelsArray, SPEC_COLORS[0]);

                int scanLineDiff = fullWidth - width;
                switch (compression) {
                    case 0 -> {
                        int pos = x + y * fullWidth;
                        for (int i1 = 0; i1 < height; i1++) {
                            for (int j1 = 0; j1 < width; j1++) {
                                pixelsArray[pos++] = palette[buf.get() & 0xff];
                            }
                            pos += scanLineDiff;
                        }
                    }
                    case 1 -> {
                        int[] offsets1 = new int[height];
                        for (int i1 = 0; i1 < offsets1.length; i1++) {
                            offsets1[i1] = buf.getInt() + start;
                        }
                        int pos = x + y * fullWidth;
                        for (int i1 : offsets1) {
                            buf.position(i1);

                            for (int w = 0; w < width; ) {
                                int index = (buf.get() & 0xff);
                                int count = (buf.get() & 0xff) + 1;
                                for (int j1 = 0; j1 < count; j1++) {
                                    int c;
                                    if (index == 0xff) {
                                        c = palette[buf.get() & 0xff];
                                    } else {
                                        c = index < SPEC_COLORS.length ? SPEC_COLORS[index] : palette[index];
                                    }
                                    pixelsArray[pos++] = c;
                                }
                                w += count;
                            }
                            pos += scanLineDiff;
                        }
                    }
                    case 2 -> {
                        int[] offsets1 = new int[height];
                        for (int i1 = 0; i1 < offsets1.length; i1++) {
                            offsets1[i1] = (buf.getShort() & 0xffff) + start;
                        }

                        int xx = x;
                        int pos = x + y * fullWidth;
                        for (int i1 : offsets1) {
                            buf.position(i1);

                            for (int w = 0; w < width; ) {
                                int b = buf.get() & 0xff;
                                int index = b >> 5;
                                int count = (b & 0x1f) + 1;
                                for (int j1 = 0; j1 < count; j1++) {
                                    pixelsArray[pos++] = index == 0x7 ? palette[buf.get() & 0xff] : SPEC_COLORS[index];
                                    xx++;
                                    if (xx >= x + width) {
                                        pos += scanLineDiff;
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
                            offsets1[i1] = (buf.getShort() & 0xffff) + start;
                        }

                        int xx = x;
                        int pos = x + y * fullWidth;
                        int a = 0;
                        for (int i1 : offsets1) {
                            System.out.print(a++);
                            buf.position(i1);

                            int left = 32;
                            while (left > 0) {
                                int b = buf.get() & 0xff;
                                int index = b >> 5;
                                int count = (b & 0x1f) + 1;
                                System.out.print(", (" + index + " " + count + ")");

                                for (int j1 = 0; j1 < count; j1++) {
                                    if (index == 0x7) {
                                        int ci = buf.get() & 0xff;
                                        System.out.print("x" + Integer.toHexString(ci).toUpperCase());
                                        pixelsArray[pos++] = palette[ci];
                                    } else {
                                        pixelsArray[pos++] = SPEC_COLORS[index];
                                    }
                                    xx++;
                                    if (xx >= x + width) {
                                        pos += scanLineDiff;
                                        xx = x;
                                    }
                                }

                                left -= count;
                            }
                            System.out.println();
                        }
                    }
                }

                DefInfo.Frame frame = new DefInfo.Frame(group, fullWidth, fullHeight, pixels);
                frame.name = names[j];
                frame.compression = compression;
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
            buffer.putInt(packedFrame.frame.compression);
            buffer.putInt(packedFrame.frame.fullWidth);
            buffer.putInt(packedFrame.frame.fullHeight);

            if (packedFrame.frame.compression == 3) {
                int x = packedFrame.box.x / 32 * 32;
                int w = packedFrame.box.width + (packedFrame.box.x - x);
                if (w % 32 != 0) {
                    w = (w / 32 + 1) * 32;
                }
                packedFrame = new PackedFrame(packedFrame.frame, new Box(x, packedFrame.box.y, w, packedFrame.box.height));
            }
            buffer.putInt(packedFrame.box.width);
            buffer.putInt(packedFrame.box.height);
            buffer.putInt(packedFrame.box.x);
            buffer.putInt(packedFrame.box.y);

            switch (packedFrame.frame.compression) {
                case 0 -> {
                    for (int y = 0; y < packedFrame.box.height; y++) {
                        for (int x = 0; x < packedFrame.box.width; x++) {
                            buffer.put(paletteMap.get(packedFrame.color(x, y)));
                        }
                    }
                }
                case 1 -> {
                    int offsetsPos = buffer.position();

                    buffer.position(offsetsPos + packedFrame.box.height * 4);
                    for (int y = 0; y < packedFrame.box.height; y++) {
                        int[] scanline = packedFrame.scanline(y);
                        int offset = buffer.position();
                        for (int x = 0; x < packedFrame.box.width; ) {
                            int color = scanline[x];
                            int index = paletteMap.get(color) & 0xff;
                            if (index < 8) {
                                int count = 1;
                                int xx = x + 1;
                                while (xx < packedFrame.box.width && count < 256 && scanline[xx] == color) {
                                    count++;
                                    xx++;
                                }
                                buffer.put((byte) index);
                                buffer.put((byte) (count - 1));
                                x = xx;
                            } else {
                                int count = 1;
                                int xx = x;
                                while (xx + 1 < packedFrame.box.width && count < 256 && paletteMap.get(scanline[xx + 1]) >= 8) {
                                    count++;
                                    xx++;
                                }

                                buffer.put((byte) 0xff);
                                buffer.put((byte) (count - 1));
                                while (x <= xx) {
                                    buffer.put(paletteMap.get(scanline[x]));
                                    x++;
                                }
                            }
                        }
                        buffer.putInt(offsetsPos + y * 4, offset - offsetsPos);
                    }
                }
                case 2 -> {
                    int offsetsPos = buffer.position();

                    buffer.position(offsetsPos + packedFrame.box.height * 2);
                    for (int y = 0; y < packedFrame.box.height; y++) {
                        int[] scanline = packedFrame.scanline(y);
                        int offset = buffer.position();
                        for (int x = 0; x < packedFrame.box.width; ) {
                            int color = scanline[x];
                            int index = paletteMap.get(color) & 0xff;
                            if (index < 6) {
                                int count = 1;
                                int xx = x + 1;
                                while (xx < packedFrame.box.width && count < 32 && scanline[xx] == color) {
                                    count++;
                                    xx++;
                                }
                                buffer.put((byte) (index << 5 | (count - 1)));
                                x = xx;
                            } else {
                                int count = 1;
                                int xx = x;
                                while (xx + 1 < packedFrame.box.width && count < 32 && paletteMap.get(scanline[xx + 1]) >= 6) {
                                    count++;
                                    xx++;
                                }

                                buffer.put((byte) (7 << 5 | (count - 1)));
                                while (x <= xx) {
                                    buffer.put(paletteMap.get(scanline[x++]));
                                }
                            }
                        }
                        buffer.putShort(offsetsPos + y * 2, (short) (offset - offsetsPos));
                    }
                }
                case 3 -> {
                    int blocksCount = packedFrame.box.width * packedFrame.box.height / 32;
                    Map<HashArray, Integer> offsets = new HashMap<>();
                    int offsetsPos = buffer.position();

                    buffer.position(offsetsPos + blocksCount * 2);

                    int i = 0;
                    for (int y = 0; y < packedFrame.box.height; y++) {
                        for (int xw = 0; xw < packedFrame.box.width; xw += 32) {
                            int[] scanline = new int[32];
                            System.arraycopy(packedFrame.scanline(y, xw, 32), 0, scanline, 0, 32);
                            int offset = buffer.position();
                            for (int x = 0; x < scanline.length; ) {
                                int color = scanline[x];
                                int index = paletteMap.get(color) & 0xff;
                                int count = 0;
                                int xx = x + 1;
                                if (index < 6) {
                                    while (xx < scanline.length && scanline[xx] == color) {
                                        count++;
                                        xx++;
                                    }
                                    buffer.put((byte) (index << 5 | count));
                                    x = xx;
                                } else {
                                    while (xx < scanline.length && (paletteMap.get(scanline[xx]) & 0xff) >= 6) {
                                        count++;
                                        xx++;
                                    }

                                    buffer.put((byte) (7 << 5 | count));
                                    while (x < xx) {
                                        buffer.put(paletteMap.get(scanline[x++]));
                                    }
                                }
                            }
                            buffer.putShort(offsetsPos + (i++ * 2), (short) (offset - offsetsPos));
                        }
                    }
                }
            }

            buffer.putInt(offsetOfSize, buffer.position() - offsetOfSize);
        }
        return ByteBuffer.wrap(Arrays.copyOf(buffer.array(), buffer.position())).order(ByteOrder.LITTLE_ENDIAN);
    }

}
