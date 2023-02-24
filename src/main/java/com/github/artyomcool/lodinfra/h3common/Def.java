package com.github.artyomcool.lodinfra.h3common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Def {

    public final ByteBuffer buffer;

    public final int type;
    public final int fullWidth;
    public final int fullHeight;

    public final List<Group> groups;
    public final int[] palette;

    public Def(String path, ByteBuffer buffer) {
        this.buffer = buffer = buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);

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
            int groupType = buffer.getInt();
            int framesCount = buffer.getInt();
            buffer.getInt();
            buffer.getInt();

            names = new String[framesCount];
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
                    System.err.println("Strange def name: " + new String(name) + "; def path: " + path + "; group index: " + i + "; frame index: " + i);
                }
            }

            offsets = new int[framesCount];
            for (int j = 0; j < framesCount; j++) {
                offsets[j] = buffer.getInt();
            }

            Group group = new Group(i);
            for (int j = 0; j < framesCount; j++) {
                Frame frame = new Frame(group, offsets[j], names[j], j);
                group.frames.add(frame);
            }

            groups.add(group);
        }
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

        public Frame(Group group, int offset, String name, int index) {
            this.group = group;
            this.offset = offset;
            this.name = name;
            this.index = index;
        }
    }

}
