package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.Resource;
import com.github.artyomcool.lodinfra.h3common.Archive;
import com.github.artyomcool.lodinfra.h3common.DefInfo;
import com.github.artyomcool.lodinfra.h3common.LodFile;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ImgFilesUtils {

    private static final Method unmapperMethod;

    static {
        try {
            Class<?> cl = Class.forName("java.nio.MappedByteBuffer");
            unmapperMethod = cl.getDeclaredMethod("unmapper");
            unmapperMethod.setAccessible(true);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public static void premultiply(int[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = premultiply(array[i]);
        }
    }

    public static void unmultiply(int[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = unmultiply(array[i]);
        }
    }

    public static int premultiply(int nonpre) {
        int a = nonpre >>> 24;
        if (a == 0xff) return nonpre;
        if (a == 0x00) return 0;
        int r = (nonpre >> 16) & 0xff;
        int g = (nonpre >> 8) & 0xff;
        int b = (nonpre) & 0xff;
        r = (r * a + 127) / 0xff;
        g = (g * a + 127) / 0xff;
        b = (b * a + 127) / 0xff;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int unmultiply(int pre) {
        int a = pre >>> 24;
        if (a == 0xff || a == 0x00) return pre;
        int r = (pre >> 16) & 0xff;
        int g = (pre >> 8) & 0xff;
        int b = (pre) & 0xff;
        int halfa = a >> 1;
        r = (r >= a) ? 0xff : (r * 0xff + halfa) / a;
        g = (g >= a) ? 0xff : (g * 0xff + halfa) / a;
        b = (b >= a) ? 0xff : (b * 0xff + halfa) / a;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static <T> T processFile(Path file, T def, ImgFilesUtils.Processor<T> processor) {
        try {
            Path lodPath = Resource.pathOfLod(file);
            if (lodPath != null) {
                try (FileChannel channel = FileChannel.open(lodPath)) {
                    MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    Archive lod = LodFile.parse(lodPath, buffer);
                    String name = Resource.fileNamePossibleInLod(file);
                    for (Archive.Element subFile : lod.files()) {
                        if (name.equals(subFile.name())) {
                            return processor.process(subFile.asByteBuffer());
                        }
                    }
                    return def;
                }
            } else {
                MappedByteBuffer buffer = null;
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size());
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    return processor.process(channel, buffer);
                } finally {
                    if (buffer != null) {
                        try {
                            Object unmapper = unmapperMethod.invoke(buffer);
                            Method unmap = unmapper.getClass().getDeclaredMethod("unmap");
                            unmap.setAccessible(true);
                            unmap.invoke(unmapper);
                        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        } catch (NoSuchFileException e) {
            return def;
        } catch (IOException e) {
            e.printStackTrace();
            return def;
        } catch (RuntimeException | Error e) {
            e.printStackTrace();
            throw e;
        }
    }
/*
    public static void validateDefNames(Path path, Pattern skipFrames) {
        processFile(path, null, buffer -> {
            Map<String, String> remap = new LinkedHashMap<>();

            ADef def = ADef.decode(buffer);
            for (int i = 0; i < def.groups().size(); i++) {
                ADef.Group<?> group = def.groups().get(i);
                for (int j = 0; j < group.frames().size(); j++) {
                    ADef.Frame<?> frame = group.frames().get(j);
                    String old = frame.name;
                    if (skipFrames.matcher(old).matches()) {
                        continue;
                    }

                    int groupIndex = group.index;
                    int frameIndex = frame.index;
                    String newName = remap.computeIfAbsent(old, k -> {
                        String defName = path.getFileName().toString();
                        defName = defName.substring(0, defName.indexOf("."));
                        if (defName.length() > 8) {
                            defName = defName.substring(0, 4) + defName.substring(defName.length() - 4);
                        }
                        return defName + (char)((int)'A' + groupIndex) + String.format("%03d", frameIndex);
                    });

                    if (!old.equals(newName)) {
                        System.err.println("Def has wrong naming: " + path + "; groupIndex: " + groupIndex + "; frameIndex: " + frameIndex + "; should be named: " + newName + "; current name:" + old);
                    }
                }
            }

            return null;
        });
    }

    public static void fixDefNames(Path path, Pattern skipFrames) {
        processFile(path, null, new Processor<Def>() {
            @Override
            public Def process(FileChannel channel, ByteBuffer buffer) throws IOException {
                Def process = Processor.super.process(channel, buffer);
                if (process.wasChanged) {
                    ((MappedByteBuffer)buffer).force();
                    Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
                }
                return process;
            }

            @Override
            public Def process(ByteBuffer buffer) throws IOException {
                Map<String, String> remap = new LinkedHashMap<>();

                return new Def(path.toString(), buffer,
                        (name, group, frame) -> {
                    if (skipFrames.matcher(name).matches()) {
                        return null;
                    }
                    return remap.computeIfAbsent(name, s -> {
                        String defName = path.getFileName().toString();
                        defName = defName.substring(0, defName.indexOf("."));
                        if (defName.length() > 8) {
                            defName = defName.substring(0, 4) + defName.substring(defName.length() - 4);
                        }
                        return defName + (char) ((int) 'A' + group) + String.format("%03d", frame);
                    });
                });
            }
        });
    }
*/
    /*
    public static void validateDefColors(Path path) {
        processFile(path, null, buffer -> {
            Def def = new Def(path.toString(), buffer);
            Set<Integer> checked = new HashSet<>();
            int fi = 0;
            for (int i = 0; i < def.groups.size(); i++) {
                Def.Group group = def.groups.get(i);
                for (int j = 0; j < group.frames.size(); j++) {
                    Def.Frame frame = group.frames.get(j);
                    if (checked.add(frame.offset)) {
                        int[][] pixels = def.decode(frame);
                        validateColors(path + ":group_" + i + ":frame_" + j + ":frameIndex:" + fi, pixels);
                        pcxToD32Colors(pixels);
                        Box box = calculateTransparent(pixels);
                        Box b2 = def.box(frame);
                        if (box.left != b2.left || box.top != b2.top || box.bottom != b2.bottom || box.right != b2.right) {
                            System.err.println("Transparent indents: " + path + ":group_" + i + ":frame_" + j);
                        }
                    }
                    fi++;
                }
            }
            return null;
        });
    }*/

    private static int cleanupColor(int c) {
        int stdColor = d32ToPcxColor(false, c);
        if (c != stdColor || (c & 0xff000000) != 0) {
            return c;
        }
        return 0;
    }

    public static boolean invalidColorDiff(int diff) {
        return diff != 0 && diff < 4;
    }

    private static void validateColors(String name, int[][] colors) {
        boolean transparencyReported = false;
        for (int y = 0; y < colors.length; y++) {
            int[] line = colors[y];
            for (int x = 0; x < line.length; x++) {
                int color = d32ToPcxColor(false, line[x]);
                if ((color & 0xff000000) == 0 && color != 0) {
                    if (!transparencyReported) {
                        System.err.println("Color is transparent but not black: " + name + " (" + x + ", " + y + ")");
                        transparencyReported = true;
                    }
                } else if (invalidColorDiff(colorDifFromStd(color))) {
                    System.err.println("Color to close to special color: " + name + " (" + x + ", " + y + ")");
                }
            }
        }
    }

    public static int colorDifFromStd(int color) {
        int diff = Integer.MAX_VALUE;
        for (int specialColor : DefInfo.SPEC_COLORS) {
            diff = Math.min(diff, colorDifference(color, specialColor));
        }
        return diff;
    }

    public static int colorDifference(int c1, int c2) {
        int a1 = (c1 >>> 24) & 0xff;
        int a2 = (c2 >>> 24) & 0xff;
        int r1 = (c1 >>> 16) & 0xff;
        int r2 = (c2 >>> 16) & 0xff;
        int g1 = (c1 >>> 8) & 0xff;
        int g2 = (c2 >>> 8) & 0xff;
        int b1 = (c1 >>> 0) & 0xff;
        int b2 = (c2 >>> 0) & 0xff;

        int delta2 = Math.max(Math.max(diff2(a1, a2), diff2(r1, r2)), Math.max(diff2(g1, g2), diff2(b1, b2)));
        return (int) Math.sqrt(delta2);
    }

    public static int colorDifferenceForCompare(int c1, int c2) {
        int a1 = (c1 >>> 24) & 0xff;
        int a2 = (c2 >>> 24) & 0xff;
        int r1 = (c1 >>> 16) & 0xff;
        int r2 = (c2 >>> 16) & 0xff;
        int g1 = (c1 >>> 8) & 0xff;
        int g2 = (c2 >>> 8) & 0xff;
        int b1 = (c1 >>> 0) & 0xff;
        int b2 = (c2 >>> 0) & 0xff;

        return diff2(a1, a2) + diff2(r1, r2) + diff2(g1, g2) + diff2(b1, b2);
    }

    private static int diff2(int a, int b) {
        return (a - b) * (a - b);
    }

    public static int pcxToD32Color(int pixel) {
        return switch (pixel) {
            case 0xFF00FFFF -> 0x00000000;
            case 0xFFFF96FF -> 0x00FF0002;
            case 0xFFFF64FF -> 0x00FF0001;
            case 0xFFFF32FF -> 0x00FF0003;
            case 0xFFFF00FF -> 0x00FF0004;
            case 0xFFFFFF00 -> 0x00FF0010;
            case 0xFFB400FF -> 0x00FF0014;
            case 0xFF00FF00 -> 0x00FF0012;
            default -> pixel;
        };
    }

    public static int d32ToPcxColor(boolean cleanPixels, int pixel) {
        return switch (pixel) {
            case 0x00000000 -> 0xFF00FFFF;
            case 0x00FF0002 -> 0xFFFF96FF;
            case 0x00FF0001 -> 0xFFFF64FF;
            case 0x00FF0003 -> 0xFFFF32FF;
            case 0x00FF0004 -> 0xFFFF00FF;
            case 0x00FF0010 -> 0xFFFFFF00;
            case 0x00FF0014 -> 0xFFB400FF;
            case 0x00FF0012 -> 0xFF00FF00;
            default -> cleanPixels && pixel >>> 24 == 0 ? 0x00000000 : pixel;
        };
    }

    public interface Processor<R> {
        R process(ByteBuffer buffer) throws IOException;

        default R process(FileChannel channel, ByteBuffer buffer) throws IOException {
            return process(buffer);
        }
    }
}
