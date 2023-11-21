package com.github.artyomcool.lodinfra.h3common;

import com.github.artyomcool.lodinfra.ui.ImgFilesUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefInfo {

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
    public static final String[] BATTLE_GROUPS = {
            "Moving",
            "Mouse-Over",
            "Standing",
            "Getting-Hit",
            "Defend",
            "Death",
            "Unused-Death",
            "Turn-Left",
            "Turn-Right",
            "Turn-Left",
            "Turn-Right",
            "Attack-Up",
            "Attack-Straight",
            "Attack-Down",
            "Shoot-Up",
            "Shoot-Straight",
            "Shoot-Down",
            "2-Hex-Attack-Up",
            "2-Hex-Attack-Straight",
            "2-Hex-Attack-Down",
            "Start-Moving",
            "Stop-Moving",
    };
    public static final String[] HERO_GROUPS = {
            "Standing",
            "Shuffle",
            "Failure",
            "Victory",
            "Cast-Spell",
    };
    public static final String[] MOVE_GROUPS = {
            "Up",
            "Up-Right",
            "Right",
            "Down-Right",
            "Down",
            "Move-Up",
            "Move-Up-Right",
            "Move-Right",
            "Move-Down-Right",
            "Move-Down",
    };
    public static final String[] UNKNOWN_GROUPS = {};

    public static String[] groupNames(DefInfo def) {
        int maxIndex = 0;
        for (Group group : def.groups) {
            maxIndex = Math.max(maxIndex, group.groupIndex);
        }
        int size = maxIndex + 1;
        if (size >= 22) {
            return BATTLE_GROUPS;
        }
        if (size >= 10) {
            return MOVE_GROUPS;
        }
        if (size >= 5) {
            return HERO_GROUPS;
        }
        return UNKNOWN_GROUPS;
    }

    /*
    def:
        0x40 - default
        0x42 - combat creatures
        0x43 - adv obj
        0x44 - adv hero
        0x45 - ground tiles
        0x46 - mouse pointer
        0x47 - interface
        0x49 - combat hero
    d32:
        0x46323344 - D32F
    p32:
        0x46323350 - P32F
    pcx:
        0x10 - type 8
        0x11 - type 24
     */
    public int type;
    public int fullWidth;
    public int fullHeight;
    public int[] palette;
    public Path path;

    public final List<Group> groups = new ArrayList<>();

    public DefInfo cloneBase() {
        DefInfo def = new DefInfo();
        def.type = type;
        def.fullWidth = fullWidth;
        def.fullHeight = fullHeight;
        def.palette = palette;
        def.path = path;
        return def;
    }

    public static class Group {
        public final DefInfo def;
        public int groupIndex;
        public String name;
        public final List<Frame> frames = new ArrayList<>();

        public Group(DefInfo def) {
            this.def = def;
        }

        public Group cloneBase(DefInfo def) {
            Group group = new Group(def);
            group.groupIndex = groupIndex;
            group.name = name;
            return group;
        }
    }

    public static class Frame {
        public final Group group;
        public String name;
        /*
        type == 43 || type == 44 -> 3
        type == 45 && w == 32 && h == 32 -> hasSpecialColors ? 2 : 0
        type == 45 -> 3
        default -> 1
         */
        public int compression = 1;    // 0 - no compression (0 spec colors), 1 - large packs (8 spec colors), 2 - small packs (6 spec colors), 3 - small packs 32 (6 spec colors)
        public int frameDrawType = 0; // 0 - normal, 1 - lightening
        public final FrameData data;
        public int[][] cachedData;

        public Frame(Group group, FrameData data) {
            this.group = group;
            this.data = data;
        }

        public Frame cloneBase(Group group) {
            Frame frame = new Frame(group, data);
            frame.name = name;
            frame.compression = compression;
            frame.frameDrawType = frameDrawType;
            frame.cachedData = cachedData;
            return frame;
        }

        public int[][] decodeFrame() {
            if (cachedData == null) {
                return cachedData = data.decodeFrame();
            }
            return cachedData;
        }
    }

    public static DefInfo load(Path path) {
        return ImgFilesUtils.processFile(path, null, buffer -> {
            try {
                byte[] bytes = Files.readAllBytes(path);
                DefInfo def = DefInfo.load(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
                if (def != null) {
                    def.path = path;
                }
                return def;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

        });
    }

    public static DefInfo load(ByteBuffer buffer) {
        int type = buffer.getInt(buffer.position());
        switch (type) {
            case 0x40,          // default def
                    0x42,       // combat creatures
                    0x43,       // adv obj
                    0x44,       // adv hero
                    0x45,       // ground tiles
                    0x46,       // mouse pointer
                    0x47,       // interface
                    0x49 -> {   // combat hero
                return Def.load(buffer);
            }
            case 0x46323344 -> { // D32F
                return D32.load(buffer);
            }
            case 0x46323350 -> { // P32F
                return P32.load(buffer);
            }
            default -> {
                int width = buffer.getInt(buffer.position() + 4);
                int height = buffer.getInt(buffer.position() + 8);
                if (width * height * 3 == type) {
                    return Pcx.load24(buffer, width, height);
                }
                if (width * height == type) {
                    return Pcx.load8(buffer, width, height);
                }
                int type2 = buffer.getShort(buffer.position());
                if (type2 == 0x4D42) {  // BM
                    return Bmp.load(buffer);
                }
                long type8 = buffer.getLong(buffer.position());
                if (type8 == 0x0A1A0A0D474E5089L) {
                    return Png.load(buffer);
                }
            }
        }
        return null;
    }

    public static void putFrameName(ByteBuffer buffer, String n) {
        byte[] name = Arrays.copyOf(n.getBytes(), 12);
        buffer.put(name);
        buffer.put((byte) 0);
    }

    protected static class PackedFrame {
        final int[][] data;

        final int fullWidth;
        final int fullHeight;

        final int width;
        final int height;
        final int x;
        final int y;

        final int compression;
        final int drawType;

        final int hashCode;

        PackedFrame(int[][] data, int fullWidth, int fullHeight, int width, int height, int x, int y, int compression, int drawType) {
            this.data = data;
            this.fullWidth = fullWidth;
            this.fullHeight = fullHeight;
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
            this.compression = compression;
            this.drawType = drawType;
            this.hashCode = calcHashCode();
        }

        private int calcHashCode() {
            int result = Arrays.deepHashCode(data);
            result = 31 * result + fullWidth;
            result = 31 * result + fullHeight;
            result = 31 * result + width;
            result = 31 * result + height;
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + compression;
            result = 31 * result + drawType;
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PackedFrame frame = (PackedFrame) o;

            if (fullWidth != frame.fullWidth) return false;
            if (fullHeight != frame.fullHeight) return false;
            if (width != frame.width) return false;
            if (height != frame.height) return false;
            if (x != frame.x) return false;
            if (y != frame.y) return false;
            if (compression != frame.compression) return false;
            if (drawType != frame.drawType) return false;
            if (hashCode != frame.hashCode) return false;
            return Arrays.deepEquals(data, frame.data);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    protected static class FrameInfo {
        final PackedFrame packedFrame;
        final List<Frame> frames = new ArrayList<>();
        String name;
        int offset = -1;

        FrameInfo(PackedFrame packedFrame) {
            this.packedFrame = packedFrame;
        }
    }

    public interface FrameData {
        int[][] decodeFrame();
    }

}
