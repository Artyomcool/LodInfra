package com.github.artyomcool.lodinfra.h3common;

import com.github.artyomcool.lodinfra.ui.ImgFilesUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

public class D32 extends DefInfo {

    public static final int TYPE = 0x46323344;  // D32F in LE

    public static DefInfo load(ByteBuffer buffer) {
        DefInfo def = new DefInfo();
        def.type = buffer.getInt();
        int version = buffer.getInt();
        int headerSize = buffer.getInt();
        def.fullWidth = buffer.getInt();
        def.fullHeight = buffer.getInt();

        int activeGroupsCount = buffer.getInt();
        int additionalHeaderSize = buffer.getInt();
        int allGroupsCount = buffer.getInt();

        int position = buffer.position();
        for (int i = 0; i < activeGroupsCount; i++) {
            buffer.position(position);

            int groupHeaderSize = buffer.getInt();
            int groupIndex = buffer.getInt();
            int framesCount = buffer.getInt();

            int additionalGroupHeaderSize = buffer.getInt();

            DefInfo.Group group = new DefInfo.Group(def);
            group.groupIndex = groupIndex;

            String[] names = new String[framesCount];
            byte[] name = new byte[13];
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

            position = buffer.position();
            for (int j = 0; j < framesCount; j++) {
                int offset = buffer.getInt(position + j * 4);

                ByteBuffer bf = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(offset);
                int frameHeaderSize = bf.getInt();
                int imageSize = bf.getInt();

                int fullWidth = bf.getInt();
                int fullHeight = bf.getInt();

                int width = bf.getInt();
                int height = bf.getInt();
                int x = bf.getInt();
                int y = bf.getInt();

                int frameInfoSize = bf.getInt();
                int frameDrawType = bf.getInt();

                IntBuffer pixels = IntBuffer.allocate(fullWidth * fullHeight);
                int[] pixArrays = pixels.array();
                Arrays.fill(pixArrays, SPEC_COLORS[0]);
                for (int yy = height - 1; yy >= 0; yy--) {
                    for (int xx = 0; xx < width; xx++) {
                        pixArrays[(yy + y) * fullWidth + xx + x] = ImgFilesUtils.d32ToPcxColor(false, bf.getInt());
                    }
                }

                ImgFilesUtils.premultiply(pixels.array());

                DefInfo.Frame frame = new DefInfo.Frame(group, fullWidth, fullHeight, pixels);
                frame.name = names[j];
                frame.frameDrawType = buffer.getInt(offset + 36);
                group.frames.add(frame);
            }
            position += framesCount * 4;

            def.groups.add(group);
        }
        return def;
    }

    public static ByteBuffer pack(DefInfo def, Map<DefInfo.Frame, FrameInfo> links) {
        int maxIndex = 0;
        for (Group group : def.groups) {
            maxIndex = Math.max(maxIndex, group.groupIndex);
        }

        int type = TYPE;
        int version = 1;
        int headerSize = 24;
        int fullWidth = def.fullWidth;
        int fullHeight = def.fullHeight;
        int activeGroupCount = def.groups.size();

        int additionalHeaderSize = 8;
        int allGroupsCount = maxIndex + 1;

        int totalSizeBeforeFrames = headerSize + additionalHeaderSize;
        int totalFrames = 0;
        int totalFramesDataSize = 0;

        for (Group group : def.groups) {
            int groupHeaderSize = 12;
            int framesCount = group.frames.size();

            int additionalGroupHeaderSize = 4;

            totalSizeBeforeFrames += groupHeaderSize + additionalGroupHeaderSize + framesCount * (13 + 4);
            totalFrames += framesCount;
        }

        for (FrameInfo f : new HashSet<>(links.values())) {
            totalFramesDataSize += f.packedFrame.box.width * f.packedFrame.box.height * 4;
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

        int framesOffset = totalSizeBeforeFrames;
        for (Group group : def.groups) {
            int groupHeaderSize = 12;
            int groupIndex = group.groupIndex;
            int framesCount = group.frames.size();

            int additionalGroupHeaderSize = 4;

            buffer
                    .putInt(groupHeaderSize + additionalGroupHeaderSize + framesCount * (13 + 4))
                    .putInt(groupIndex)
                    .putInt(framesCount)
                    .putInt(additionalGroupHeaderSize);

            for (Frame f : group.frames) {
                putFrameName(buffer, links.get(f).name);
            }

            for (Frame f : group.frames) {
                FrameInfo info = links.get(f);
                if (info.offset == -1) {
                    info.offset = framesOffset;
                    buffer.putInt(framesOffset);
                    int backup = buffer.position();
                    buffer.position(framesOffset);

                    int frameHeaderSize = 40;
                    int imageSize = info.packedFrame.box.width * info.packedFrame.box.height * 4;

                    int fw = info.packedFrame.frame.fullWidth;
                    int fh = info.packedFrame.frame.fullHeight;

                    int width = info.packedFrame.box.width;
                    int height = info.packedFrame.box.height;
                    int x = info.packedFrame.box.x;
                    int y = info.packedFrame.box.y;

                    int frameInfoSize = 8;
                    int frameDrawType = info.packedFrame.frame.frameDrawType;

                    buffer
                            .putInt(frameHeaderSize)
                            .putInt(imageSize)
                            .putInt(fw)
                            .putInt(fh)
                            .putInt(width)
                            .putInt(height)
                            .putInt(x)
                            .putInt(y)
                            .putInt(frameInfoSize)
                            .putInt(frameDrawType);

                    IntBuffer intBuffer = buffer.asIntBuffer();
                    for (int j = height - 1; j >= 0; j--) {
                        for (int i = 0; i < width; i++) {
                            buffer.putInt(info.packedFrame.color(i, j));
                        }
                    }

                    framesOffset = buffer.position() + intBuffer.position() * 4;
                    buffer.position(backup);
                } else {
                    buffer.putInt(info.offset);
                }
            }
        }

        buffer.position(0);
        buffer.limit(totalSize);
        return buffer;
    }
}
