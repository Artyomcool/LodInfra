package com.github.artyomcool.lodinfra.h3common;

import com.github.artyomcool.lodinfra.ui.ImgFilesUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DefInfo {

    public static final int[] SPEC_COLORS = new int[]{
            0xFF00FFFF,
            0xFFFF96FF,
            0xFFFF64FF,
            0xFFFF32FF,
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
        public final int fullWidth;
        public final int fullHeight;
        public final IntBuffer pixels;

        public String name;
        /*
        type == 43 || type == 44 -> 3
        type == 45 && w == 32 && h == 32 -> hasSpecialColors ? 2 : 0
        type == 45 -> 3
        default -> 1
         */
        public int compression = 1;    // 0 - no compression (0 spec colors), 1 - large packs (8 spec colors), 2 - small packs (6 spec colors), 3 - small packs 32 (6 spec colors)
        public int frameDrawType = 0; // 0 - normal, 1 - lightening

        public Frame(Group group, int fullWidth, int fullHeight, IntBuffer pixels) {
            this.group = group;
            this.fullWidth = fullWidth;
            this.fullHeight = fullHeight;
            this.pixels = pixels;
        }

        public static IntBuffer emptyPixels(int w, int h) {
            int[] result = new int[w * h];
            Arrays.fill(result, SPEC_COLORS[0]);
            return IntBuffer.wrap(result);
        }

        public Frame cloneBase(Group group) {
            Frame frame = new Frame(group, fullWidth, fullHeight, pixels);
            frame.name = name;
            frame.compression = compression;
            frame.frameDrawType = frameDrawType;
            return frame;
        }

        public int color(int x, int y) {
            return ImgFilesUtils.unmultiply(pixels.get(x + y * fullWidth));
        }

        public IntBuffer pixelsWithSize(int w, int h) {
            if (fullWidth == w && fullHeight == h) {
                return pixels.duplicate();
            }

            IntBuffer buffer = emptyPixels(w, h);
            for (int y = 0; y < fullHeight; y++) {
                buffer.put(y * w, pixels, y * fullWidth, fullWidth);
            }

            return buffer;
        }
    }

    public static DefInfo load(Path path) {
        return ImgFilesUtils.processFile(path, null, buffer -> {
            DefInfo def = DefInfo.load(buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN));
            if (def != null) {
                def.path = path;
            }
            return def;

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
        final Frame frame;
        final ImgFilesUtils.Box box;
        final int hashCode;

        PackedFrame(Frame frame, ImgFilesUtils.Box box) {
            this.frame = frame;
            this.box = box;
            this.hashCode = calcHashCode();
        }

        private int calcHashCode() {
            int result = box.hashCode();
            result = 31 * result + frame.fullWidth;
            result = 31 * result + frame.fullHeight;
            result = 31 * result + frame.compression;
            result = 31 * result + frame.frameDrawType;
            result = 31 * result + frame.pixels.hashCode();
            return result;
        }

        public int color(int x, int y) {
            return frame.color(x + box.x, y + box.y);
        }

        public int[] scanline(int y) {
            int[] scanline = new int[box.width];
            frame.pixels.get(box.x + (y + box.y) * frame.fullWidth, scanline);
            for (int i = 0; i < scanline.length; i++) {
                scanline[i] = ImgFilesUtils.unmultiply(scanline[i]);
            }
            return scanline;
        }

        public int[] scanline(int y, int dx, int w) {
            int[] scanline = new int[w];
            frame.pixels.get(box.x + dx  + (y + box.y) * frame.fullWidth, scanline);
            for (int i = 0; i < scanline.length; i++) {
                scanline[i] = ImgFilesUtils.unmultiply(scanline[i]);
            }
            return scanline;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PackedFrame that = (PackedFrame) o;

            if (hashCode != that.hashCode) return false;
            if (frame.fullWidth != that.frame.fullWidth) return false;
            if (frame.fullHeight != that.frame.fullHeight) return false;
            if (frame.compression != that.frame.compression) return false;
            if (frame.frameDrawType != that.frame.frameDrawType) return false;
            if (!Objects.equals(box, that.box)) return false;
            return Objects.equals(frame.pixels, that.frame.pixels);
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
}
