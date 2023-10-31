package com.github.artyomcool.lodinfra.h3common;

import com.github.artyomcool.lodinfra.ui.ImgFilesUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;

public class D32 {

    public final ByteBuffer buffer;

    public final int type;
    public final int version;
    public final int fullWidth;
    public final int fullHeight;

    public final List<Group> groups;

    public boolean changed;

    public static D32 pack(Path path, List<GroupDescriptor> groups, Map<String, int[][]> framesData) throws IOException {
        class PackedFrame {
            int[][] data;

            int width;
            int height;

            int nonZeroColorWidth;
            int nonZeroColorHeight;
            int nonZeroColorLeft;
            int nonZeroColorTop;
        }

        class HashedArray {
            final int[][] data;
            final int hash;

            HashedArray(int[][] data) {
                this.data = data;
                this.hash = Arrays.deepHashCode(data);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                HashedArray that = (HashedArray) o;
                return Arrays.deepEquals(data, that.data);
            }

            @Override
            public int hashCode() {
                return hash;
            }
        }
        int maxIndex = 0;
        for (GroupDescriptor group : groups) {
            maxIndex = Math.max(group.index, maxIndex);
        }

        Map<String, PackedFrame> result = new HashMap<>();
        Map<HashedArray, int[][]> rehash = new HashMap<>();
        int maxWidth = 0;
        int maxHeight = 0;

        for (Map.Entry<String, int[][]> entry : framesData.entrySet()) {
            String key = entry.getKey();
            int[][] value = entry.getValue();
            ImgFilesUtils.Box box = ImgFilesUtils.calculateTransparent(value);

            int[][] data = new int[box.bottom - box.top + 1][box.right - box.left + 1];
            for (int y = 0, fy = box.top; y < data.length; y++, fy++) {
                int[] dstLine = data[y];
                int[] srcLine = value[fy];

                System.arraycopy(srcLine, box.left, dstLine, 0, dstLine.length);
            }

            HashedArray ha = new HashedArray(data);
            int[][] old = rehash.putIfAbsent(ha, data);
            if (old != null) {
                data = old;
            }

            PackedFrame frame = new PackedFrame();
            frame.data = data;
            frame.width = value.length == 0 ? 0 : value[0].length;
            frame.height = value.length;
            frame.nonZeroColorLeft = box.left;
            frame.nonZeroColorTop = box.top;
            frame.nonZeroColorWidth = box.right - box.left + 1;
            frame.nonZeroColorHeight = box.bottom - box.top + 1;
            result.put(key, frame);

            maxWidth = Math.max(frame.width, maxWidth);
            maxHeight = Math.max(frame.height, maxHeight);
        }

        int type = 0x46323344;  // D32F in LE
        int version = 1;
        int headerSize = 24;
        int fullWidth = maxWidth;
        int fullHeight = maxHeight;
        int activeGroupCount = groups.size();

        int additionalHeaderSize = 8;
        int allGroupsCount = maxIndex + 1;

        int totalSizeBeforeFrames = headerSize + additionalHeaderSize;
        int totalFrames = 0;
        int totalFramesDataSize = 0;

        for (GroupDescriptor group : groups) {
            int groupHeaderSize = 12;
            int framesCount = group.frameDescriptors.size();

            int additionalGroupHeaderSize = 4;

            totalSizeBeforeFrames += groupHeaderSize + additionalGroupHeaderSize + framesCount * (13 + 4);
            totalFrames += framesCount;
        }

        for (PackedFrame f : result.values()) {
            totalFramesDataSize += f.nonZeroColorWidth * f.nonZeroColorHeight * 4;
        }

        int framesHeaderSize = 40;

        int totalSize = totalSizeBeforeFrames + totalFrames * framesHeaderSize + totalFramesDataSize;
        byte[] data = new byte[totalSize];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buffer
                .putInt(type)
                .putInt(version)
                .putInt(headerSize)
                .putInt(fullWidth)
                .putInt(fullHeight)
                .putInt(activeGroupCount);

        buffer
                .putInt(additionalHeaderSize)
                .putInt(allGroupsCount);

        Map<String, Integer> frames = new HashMap<>();

        int framesOffset = totalSizeBeforeFrames;
        for (GroupDescriptor group : groups) {
            int groupHeaderSize = 12;
            int groupIndex = group.index;
            int framesCount = group.frameDescriptors.size();

            int additionalGroupHeaderSize = 4;

            buffer
                    .putInt(groupHeaderSize + additionalGroupHeaderSize + framesCount * (13 + 4))
                    .putInt(groupIndex)
                    .putInt(framesCount)
                    .putInt(additionalGroupHeaderSize);

            for (FrameDescriptor frameDescriptor : group.frameDescriptors) {
                byte[] name = Arrays.copyOf(frameDescriptor.name.getBytes(), 12);
                buffer.put(name);
                buffer.put((byte) 0);
            }

            for (FrameDescriptor frameDescriptor : group.frameDescriptors) {
                PackedFrame packedFrame = result.get(frameDescriptor.name);
                Integer frame = frames.get(frameDescriptor.name);
                if (frame == null) {
                    frames.put(frameDescriptor.name, framesOffset);
                    buffer.putInt(framesOffset);
                    int backup = buffer.position();
                    buffer.position(framesOffset);

                    int frameHeaderSize = 40;
                    int imageSize = packedFrame.nonZeroColorWidth * packedFrame.nonZeroColorHeight * 4;

                    int width = packedFrame.width;
                    int height = packedFrame.height;

                    int nonZeroColorWidth = packedFrame.nonZeroColorWidth;
                    int nonZeroColorHeight = packedFrame.nonZeroColorHeight;
                    int nonZeroColorLeft = packedFrame.nonZeroColorLeft;
                    int nonZeroColorTop = packedFrame.nonZeroColorTop;

                    int frameInfoSize = 8;
                    int frameDrawType = frameDescriptor.frameDrawType;

                    buffer
                            .putInt(frameHeaderSize)
                            .putInt(imageSize)
                            .putInt(width)
                            .putInt(height)
                            .putInt(nonZeroColorWidth)
                            .putInt(nonZeroColorHeight)
                            .putInt(nonZeroColorLeft)
                            .putInt(nonZeroColorTop)
                            .putInt(frameInfoSize)
                            .putInt(frameDrawType);

                    IntBuffer intBuffer = buffer.asIntBuffer();
                    int[][] d = packedFrame.data;
                    for (int i = d.length - 1; i >= 0; i--) {
                        int[] scanline = d[i];
                        intBuffer.put(scanline);
                    }

                    framesOffset = buffer.position() + intBuffer.position() * 4;
                    buffer.position(backup);
                } else {
                    buffer.putInt(frame);
                }
            }

            totalSizeBeforeFrames += groupHeaderSize + additionalGroupHeaderSize + framesCount * (13 + 4);
            totalFrames += framesCount;
            for (FrameDescriptor frame : group.frameDescriptors) {
                PackedFrame f = result.get(frame.name);
                totalFramesDataSize += f.width * f.height * 4;
            }
        }

        buffer.position(0);
        buffer.limit(totalSize);

        Files.write(path, data);
        return new D32(path.toString(), buffer);
    }

    public D32(String path, ByteBuffer buffer) {
        this.buffer = buffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);

        type = buffer.getInt();
        version = buffer.getInt();
        int headerSize = buffer.getInt();
        fullWidth = buffer.getInt();
        fullHeight = buffer.getInt();
        int activeGroupsCount = buffer.getInt();
        int additionalHeaderSize = buffer.getInt();
        int allGroupsCount = buffer.getInt();

        List<Group> groups = new ArrayList<>(activeGroupsCount);

        int position = buffer.position();
        for (int i = 0; i < activeGroupsCount; i++) {
            buffer.position(position);

            int groupHeaderSize = buffer.getInt();
            int groupIndex = buffer.getInt();
            int framesCount = buffer.getInt();

            int additionalGroupHeaderSize = buffer.getInt();

            Group group = new Group(groupIndex);

            String[] names = new String[framesCount];
            byte[] nbuf = new byte[13];
            for (int f = 0; f < framesCount; f++) {
                buffer.get(nbuf);
                int q = 0;
                for (; q < 12; q++) {
                    if (nbuf[q] == 0) {
                        break;
                    }
                }
                names[f] = new String(nbuf, 0, q);
            }

            position = buffer.position();
            for (int j = 0; j < framesCount; j++) {
                int offset = buffer.getInt(position + j * 4);

                buffer.position(offset);

                int frameHeaderSize = buffer.getInt();
                int imageSize = buffer.getInt();

                int width = buffer.getInt();
                int height = buffer.getInt();

                int nonZeroColorWidth = buffer.getInt();
                int nonZeroColorHeight = buffer.getInt();
                int nonZeroColorLeft = buffer.getInt();
                int nonZeroColorTop = buffer.getInt();

                int frameInfoSize = buffer.getInt();
                int frameDrawType = buffer.getInt();

                Frame frame = new Frame(group, offset, names[j], j, frameDrawType);
                group.frames.add(frame);
            }
            position += framesCount * 4;

            groups.add(group);
        }

        this.groups = groups;
    }

    public int[][] decode(Frame frame) {
        buffer.position(frame.offset);

        int frameHeaderSize = buffer.getInt();
        int imageSize = buffer.getInt();

        int width = buffer.getInt();
        int height = buffer.getInt();

        int nonZeroColorWidth = buffer.getInt();
        int nonZeroColorHeight = buffer.getInt();
        int nonZeroColorLeft = buffer.getInt();
        int nonZeroColorTop = buffer.getInt();

        int frameInfoSize = buffer.getInt();
        int frameDrawType = buffer.getInt();

        int[][] image = new int[height][width];

        for (int y = nonZeroColorHeight + nonZeroColorTop - 1; y >= nonZeroColorTop; y--) {
            for (int x = nonZeroColorLeft; x < nonZeroColorLeft + nonZeroColorWidth; x++) {
                image[y][x] = buffer.getInt();
            }
        }

        return image;
    }

    public void changeColors(Frame frame, IntUnaryOperator changeColor) {
        buffer.position(frame.offset);

        int frameHeaderSize = buffer.getInt();
        int imageSize = buffer.getInt();

        int width = buffer.getInt();
        int height = buffer.getInt();

        int nonZeroColorWidth = buffer.getInt();
        int nonZeroColorHeight = buffer.getInt();
        int nonZeroColorLeft = buffer.getInt();
        int nonZeroColorTop = buffer.getInt();

        int frameInfoSize = buffer.getInt();
        int frameDrawType = buffer.getInt();

        for (int y = nonZeroColorHeight + nonZeroColorTop - 1; y >= nonZeroColorTop; y--) {
            for (int x = nonZeroColorLeft; x < nonZeroColorLeft + nonZeroColorWidth; x++) {
                int position = buffer.position();
                int originalColor = buffer.getInt();
                int changedColor = changeColor.applyAsInt(originalColor);
                if (originalColor != changedColor) {
                    buffer.putInt(position, changedColor);
                    changed = true;
                }
            }
        }
    }

    public static class Group {
        public final int index;
        public final List<Frame> frames = new ArrayList<>();

        public Group(int index) {
            this.index = index;
        }
    }

    public static class GroupDescriptor {
        public final int index;
        public final List<FrameDescriptor> frameDescriptors = new ArrayList<>();

        public GroupDescriptor(int index) {
            this.index = index;
        }
    }

    public static class FrameDescriptor {
        public final String name;
        public final int frameDrawType;

        public FrameDescriptor(String name, int frameDrawType) {
            this.name = name;
            this.frameDrawType = frameDrawType;
        }
    }

    public static class Frame {
        public final Group group;
        public final int offset;
        public final String name;
        public final int index;
        public final int frameDrawType;

        public Frame(Group group, int offset, String name, int index, int frameDrawType) {
            this.group = group;
            this.offset = offset;
            this.name = name;
            this.index = index;
            this.frameDrawType = frameDrawType;
        }
    }

}
