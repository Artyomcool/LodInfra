package com.github.artyomcool.lodinfra.h3common;

import com.github.artyomcool.lodinfra.ui.ImgFilesUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Def {

    public static final int[] SPEC_COLORS = new int[] {
            0xFF00FFFF,
            0xFFFF96FF,
            0xFF00BFBF,
            0xFFE343C0,
            0xFFFF00FF,
            0xFFFFFF00,
            0xFFB400FF,
            0xFF00FF00,
    };

    public final ByteBuffer buffer;

    public final int type;
    public final int fullWidth;
    public final int fullHeight;

    public final List<Group> groups;
    public final int[] palette;

    public boolean wasChanged = false;

    public Def(String path, ByteBuffer buffer) {
        this(path, buffer, (a, g, f) -> null);
    }

    public Def(String path, ByteBuffer buffer, NameHandler namesHandler) {
        this.buffer = buffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);

        type = buffer.getInt();
        fullWidth = buffer.getInt();
        fullHeight = buffer.getInt();

        int groupCount = buffer.getInt();
        palette = new int[256];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = 0xff000000 | (buffer.get() & 0xff) << 16 | (buffer.get() & 0xff) << 8 | (buffer.get() & 0xff);
        }

        /*
        palette[0] = 0xFF00FFFF;
        palette[1] = 0xFFFF80FF;
        palette[4] = 0xFFFF00FF;
        palette[5] = 0xFFFFFF00;
        palette[6] = 0xFF8000FF;
        palette[7] = 0xFF00FF00;
         */

        groups = new ArrayList<>(groupCount);

        byte[] name = new byte[13];
        String[] names;
        int[] offsets;

        for (int i = 0; i < groupCount; i++) {
            int groupIndex = buffer.getInt();
            int framesCount = buffer.getInt();
            buffer.getInt();
            buffer.getInt();

            names = new String[framesCount];
            for (int j = 0; j < framesCount; j++) {
                int startPos = buffer.position();
                buffer.get(name);
                boolean isStrange = false;
                try {
                    int q = 0;
                    while (name[q] != 0) {
                        q++;
                    }
                    names[j] = new String(name, 0, q);
                } catch (IndexOutOfBoundsException e) {
                    isStrange = true;
                    names[j] = new String(name);
                }
                String betterName = namesHandler.handleFrameName(names[j], groupIndex, j);
                if (betterName != null && (!betterName.equals(names[j]) || isStrange)) {
                    names[j] = betterName;
                    byte[] bytes = Arrays.copyOf(betterName.getBytes(StandardCharsets.UTF_8), 13);
                    for (byte b : bytes) {
                        buffer.put(startPos++, b);
                    }
                    this.wasChanged = true;
                }
            }

            offsets = new int[framesCount];
            for (int j = 0; j < framesCount; j++) {
                offsets[j] = buffer.getInt();
            }

            Group group = new Group(groupIndex);
            for (int j = 0; j < framesCount; j++) {
                Frame frame = new Frame(group, offsets[j], names[j], j, buffer.getInt(offsets[j] + 4));
                group.frames.add(frame);
            }

            groups.add(group);
        }
    }

    public ImgFilesUtils.Box box(Frame frame) {
        buffer.position(frame.offset);

        int size = buffer.getInt();
        int compression = buffer.getInt();
        int fullWidth = buffer.getInt();
        int fullHeight = buffer.getInt();

        int width = buffer.getInt();
        int height = buffer.getInt();
        int x = buffer.getInt();
        int y = buffer.getInt();

        return new ImgFilesUtils.Box(x, y, x + width - 1, y + height - 1);
    }

    public boolean verifySpecColors(Frame frame) {
        buffer.position(frame.offset);

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

        switch (compression) {
            case 0 -> {}
            case 1 -> {
                int[] offsets = new int[height];
                for (int i = 0; i < offsets.length; i++) {
                    offsets[i] = buffer.getInt() + start;
                }
                for (int i : offsets) {
                    buffer.position(i);

                    for (int w = 0; w < width; ) {
                        int index = (buffer.get() & 0xff);
                        int count = (buffer.get() & 0xff) + 1;
                        for (int j = 0; j < count; j++) {
                            if (index == 0xff) {
                                int ci = buffer.get() & 0xff;
                                if (ci < 6) {
                                    return false;
                                }
                            }
                            xx++;
                        }
                        w += count;
                    }
                    xx = x;
                    yy++;
                }
            }
            case 2 -> {
                int[] offsets = new int[height];
                for (int i = 0; i < offsets.length; i++) {
                    offsets[i] = (buffer.getShort() & 0xffff) + start;
                }
                for (int i : offsets) {
                    buffer.position(i);

                    for (int w = 0; w < width; ) {
                        int b = buffer.get() & 0xff;
                        int index = b >> 5;
                        int count = (b & 0x1f) + 1;
                        for (int j = 0; j < count; j++) {
                            if (index == 0x7) {
                                int ci = buffer.get() & 0xff;
                                if (ci < 6) {
                                    return false;
                                }
                            }
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
                int[] offsets = new int[width * height / 32];
                for (int i = 0; i < offsets.length; i++) {
                    offsets[i] = (buffer.getShort() & 0xffff) + start;
                }
                for (int i : offsets) {
                    buffer.position(i);

                    int left = 32;
                    while (left > 0) {
                        int b = buffer.get() & 0xff;
                        int index = b >> 5;
                        int count = (b & 0x1f) + 1;

                        for (int j = 0; j < count; j++) {
                            if (index == 0x7) {
                                int ci = buffer.get() & 0xff;
                                if (ci < 6) {
                                    return false;
                                }
                            }
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
        return true;
    }

    public int[][] decode(Frame frame) {
        buffer.position(frame.offset);

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
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        image[y + i][x + j] = palette[buffer.get() & 0xff];
                    }
                }
            }
            case 1 -> {
                int[] offsets = new int[height];
                for (int i = 0; i < offsets.length; i++) {
                    offsets[i] = buffer.getInt() + start;
                }
                for (int i : offsets) {
                    buffer.position(i);

                    for (int w = 0; w < width; ) {
                        int index = (buffer.get() & 0xff);
                        int count = (buffer.get() & 0xff) + 1;
                        for (int j = 0; j < count; j++) {
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
                int[] offsets = new int[height];
                for (int i = 0; i < offsets.length; i++) {
                    offsets[i] = (buffer.getShort() & 0xffff) + start;
                }
                for (int i : offsets) {
                    buffer.position(i);

                    for (int w = 0; w < width; ) {
                        int b = buffer.get() & 0xff;
                        int index = b >> 5;
                        int count = (b & 0x1f) + 1;
                        for (int j = 0; j < count; j++) {
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
                int[] offsets = new int[width * height / 32];
                for (int i = 0; i < offsets.length; i++) {
                    offsets[i] = (buffer.getShort() & 0xffff) + start;
                }
                for (int i : offsets) {
                    buffer.position(i);

                    int left = 32;
                    while (left > 0) {
                        int b = buffer.get() & 0xff;
                        int index = b >> 5;
                        int count = (b & 0x1f) + 1;

                        for (int j = 0; j < count; j++) {
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
    }

    public static class Group {
        public final int index;
        public final List<Frame> frames = new ArrayList<>();

        public Group(int index) {
            this.index = index;
        }
    }

    public static class Frame {
        public final Group group;
        public final int offset;
        public final String name;
        public final int index;
        public final int compression;

        public Frame(Group group, int offset, String name, int index, int compression) {
            this.group = group;
            this.offset = offset;
            this.name = name;
            this.index = index;
            this.compression = compression;
        }
    }

    public interface NameHandler {
        String handleFrameName(String name, int group, int frame);
    }

}
