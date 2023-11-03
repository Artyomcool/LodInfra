package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.h3common.D32;
import com.github.artyomcool.lodinfra.h3common.Def;
import com.github.artyomcool.lodinfra.h3common.LodFile;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.function.IntUnaryOperator;
import java.util.regex.Pattern;

public class ImgFilesUtils {

    public static class Box {
        public final int left, top, right, bottom;

        public Box(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    public static Box calculateTransparent(int[][] pixels) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;

        for (int y = 0; y < pixels.length; y++) {
            int[] line = pixels[y];
            for (int x = 0; x < line.length; x++) {
                if (line[x] != 0) {
                    left = Math.min(x, left);
                    right = Math.max(x, right);
                    top = Math.min(y, top);
                    bottom = Math.max(y, bottom);
                }
            }
        }

        return new Box(left, top, right, bottom);
    }

    public static List<Image> loadD32(Path file) {
        return processFile(file, Collections.emptyList(), buffer -> {
            D32 d32 = new D32(file.toString(), buffer);
            return new ArrayList<>(loadD32(d32, true, true).values());
        });
    }

    public static Map<D32.Frame, Image> loadD32(D32 d32, boolean toPcxColors, boolean cleanPixels) {
        Map<D32.Frame, Image> result = new LinkedHashMap<>();
        for (D32.Group group : d32.groups) {
            for (D32.Frame frame : group.frames) {
                int[][] pixels = d32.decode(frame);
                if (toPcxColors) {
                    d32ToPcxColors(pixels, cleanPixels);
                }

                WritableImage image = new WritableImage(pixels.length == 0 ? 0 : pixels[0].length, pixels.length);
                for (int y = 0; y < pixels.length; y++) {
                    for (int x = 0; x < pixels[y].length; x++) {
                        image.getPixelWriter().setArgb(x, y, pixels[y][x]);
                    }
                }

                result.put(frame, image);
            }
        }
        return result;
    }

    static Map<Def.Frame, Image> loadDef(Path file) {
        return processFile(
                file,
                Collections.emptyMap(),
                buffer -> loadDef(new Def(file.toString(), buffer))
        );
    }

    static Map<Def.Frame, Image> loadDef(Def def) {
        Map<String, Image> deduplication = new HashMap<>();
        Map<Def.Frame, Image> result = new LinkedHashMap<>();

        for (Def.Group group : def.groups) {
            for (Def.Frame frame : group.frames) {
                String key = frame.name.toLowerCase();
                Image image = deduplication.get(key);
                if (image == null) {
                    image = decodeDefFrame(def.buffer, def.palette, frame.offset);
                    deduplication.put(key, image);
                }
                result.put(frame, image);
            }
        }

        return result;
    }

    public static <T> T processFile(Path file, T def, ImgFilesUtils.Processor<T> processor) {
        try {
            String s = file.getFileName().toString();
            if (s.contains("?")) {
                Path p = file.resolveSibling(s.substring(0, s.indexOf("?")));
                try (FileChannel channel = FileChannel.open(p)) {
                    MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    LodFile lod = LodFile.parse(file, buffer);
                    String name = s.substring(s.indexOf("?") + 1);
                    for (LodFile.SubFileMeta subFile : lod.subFiles) {
                        if (name.equals(subFile.nameAsString)) {
                            return processor.process(subFile.asByteBuffer());
                        }
                    }
                    return def;
                }
            } else {
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size());
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    return processor.process(channel, buffer);
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

    public static Image loadP32(Path file) {
        return processFile(file, null, buffer -> {
            int type = buffer.getInt();
            int version = buffer.getInt();
            int headerSize = buffer.getInt();
            int fileSize = buffer.getInt();
            int imageOffset = buffer.getInt();
            int imageSize = buffer.getInt();

            int width = buffer.getInt();
            int height = buffer.getInt();

            WritableImage image = new WritableImage(width, height);
            buffer.position(imageOffset);
            for (int j = height - 1; j >= 0; j--) {
                for (int i = 0; i < width; i++) {
                    image.getPixelWriter().setArgb(i, j, buffer.getInt());
                }
            }

            return image;
        });
    }

    public static int[][] decodeP32(ByteBuffer buffer) {
        int type = buffer.getInt();
        int version = buffer.getInt();
        int headerSize = buffer.getInt();
        int fileSize = buffer.getInt();
        int imageOffset = buffer.getInt();
        int imageSize = buffer.getInt();

        int width = buffer.getInt();
        int height = buffer.getInt();

        int[][] image = new int[height][width];
        buffer.position(imageOffset);
        for (int j = height - 1; j >= 0; j--) {
            for (int i = 0; i < width; i++) {
                image[j][i] = buffer.getInt();
            }
        }
        return image;
    }

    public static Image decodeDefFrame(ByteBuffer buffer, int[] palette, int offset) {
        int[] spec = new int[] {
                0xFF00FFFF,
                0xFFFF96FF,
                0xFF00BFBF,
                0xFFE343C0,
                0xFFFF00FF,
                0xFFFFFF00,
                0xFFB400FF,
                0xFF00FF00,
        };

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

        WritableImage image = new WritableImage(fullWidth, fullHeight);

        switch (compression) {
            case 0 -> {
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        image.getPixelWriter().setArgb(x + j, y + i, palette[buffer.get() & 0xff]);
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
                            int color = index == 0xff ? palette[buffer.get() & 0xff] : spec[index];
                            image.getPixelWriter().setArgb(xx, yy, color);
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
                buffer.getShort();
                for (int i : offsets) {
                    buffer.position(i);

                    for (int w = 0; w < width; ) {
                        int b = buffer.get() & 0xff;
                        int index = b >> 5;
                        int count = (b & 0x1f) + 1;
                        for (int j = 0; j < count; j++) {
                            int color = index == 0x7 ? palette[buffer.get() & 0xff] : spec[index];
                            image.getPixelWriter().setArgb(xx, yy, color);
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
                            int color = index == 0x7 ? palette[buffer.get() & 0xff] : spec[index];
                            image.getPixelWriter().setArgb(xx, yy, color);
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

    public static void validateDefNames(Path path, Pattern skipFrames) {
        processFile(path, null, buffer -> {
            Map<String, String> remap = new LinkedHashMap<>();

            Def def = new Def(path.toString(), buffer);
            for (int i = 0; i < def.groups.size(); i++) {
                Def.Group group = def.groups.get(i);
                for (int j = 0; j < group.frames.size(); j++) {
                    Def.Frame frame = group.frames.get(j);
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
    }

    public static void validateD32Colors(Path path) {
        processFile(path, null, buffer -> {
            D32 d32 = new D32(path.toString(), buffer);
            Set<Integer> checked = new HashSet<>();
            for (int i = 0; i < d32.groups.size(); i++) {
                D32.Group group = d32.groups.get(i);
                for (int j = 0; j < group.frames.size(); j++) {
                    D32.Frame frame = group.frames.get(j);
                    if (checked.add(frame.offset)) {
                        int[][] pixels = d32.decode(frame, false);
                        validateColors(path + ":group_" + i + ":frame_" + j, pixels);
                        Box box = calculateTransparent(pixels);
                        if (box.left != 0 || box.top != 0 || box.bottom != pixels.length - 1 || box.right != pixels[0].length - 1) {
                            System.err.println("Transparent indents: " + path + ":group_" + i + ":frame_" + j);
                        }
                    }
                }
            }
            return null;
        });
    }

    public static void validateP32Colors(Path path) {
        processFile(path, null, buffer -> {
            validateColors(path.toString(), decodeP32(buffer));
            return null;
        });
    }

    public static void validatePcxColors(Path path) {
        //processFile(path, null, buffer -> {

        //});
    }

    public static void fixD32Colors(Path path) {
        processFile(path, null, new Processor<D32>() {
            @Override
            public D32 process(FileChannel channel, ByteBuffer buffer) throws IOException {
                D32 process = Processor.super.process(channel, buffer);
                if (process.changed) {
                    ((MappedByteBuffer)buffer).force();
                    Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
                }
                return process;
            }
            @Override
            public D32 process(ByteBuffer buffer) {
                D32 d32 = new D32(path.toString(), buffer);
                Set<Integer> checked = new HashSet<>();
                for (int i = 0; i < d32.groups.size(); i++) {
                    D32.Group group = d32.groups.get(i);
                    for (int j = 0; j < group.frames.size(); j++) {
                        D32.Frame frame = group.frames.get(j);
                        if (checked.add(frame.offset)) {
                            d32.changeColors(frame, ImgFilesUtils::cleanupColor);
                        }
                    }
                }
                return d32;
            }
        });
    }

    private static int cleanupColor(int c) {
        int stdColor = d32ToPcxColor(false, c);
        if (c != stdColor || (c & 0xff000000) != 0) {
            return c;
        }
        return 0;
    }

    public static void fixP32Colors(Path path) {
        processFile(path, null, new Processor<Object>() {
            @Override
            public Object process(FileChannel channel, ByteBuffer buffer) throws IOException {
                boolean changed = false;
                int type = buffer.getInt();
                int version = buffer.getInt();
                int headerSize = buffer.getInt();
                int fileSize = buffer.getInt();
                int imageOffset = buffer.getInt();
                int imageSize = buffer.getInt();

                int width = buffer.getInt();
                int height = buffer.getInt();

                buffer.position(imageOffset);
                for (int j = height - 1; j >= 0; j--) {
                    for (int i = 0; i < width; i++) {
                        int position = buffer.position();
                        int originalColor = buffer.getInt();
                        int changedColor = cleanupColor(originalColor);
                        if (originalColor != changedColor) {
                            buffer.putInt(position, changedColor);
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    ((MappedByteBuffer)buffer).force();
                    Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
                }
                return null;
            }
            @Override
            public Object process(ByteBuffer buffer) {
                return null;
            }
        });
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
        for (int specialColor : Def.SPEC_COLORS) {
            diff = Math.min(diff, colorDifference(color, specialColor));
        }
        return diff;
    }

    public static void pcxToD32Colors(int[][] pixels) {
        for (int[] pixel : pixels) {
            for (int i = 0; i < pixel.length; i++) {
                pixel[i] = switch (pixel[i]) {
                    case 0xFF00FFFF -> 0x00000000;
                    case 0xFFFF96FF -> 0x00FF0002;
                    case 0xFF00BFBF -> 0x00FF0001;
                    case 0xFFE343C0 -> 0x00FF0003;
                    case 0xFFFF00FF -> 0x00FF0004;
                    case 0xFFFFFF00 -> 0x00FF0010;
                    case 0xFFB400FF -> 0x00FF0014;
                    case 0xFF00FF00 -> 0x00FF0012;
                    default -> pixel[i];
                };
            }
        }
    }

    public static void d32ToPcxColors(int[][] pixels, boolean cleanPixels) {
        for (int[] pixel : pixels) {
            for (int i = 0; i < pixel.length; i++) {
                pixel[i] = d32ToPcxColor(cleanPixels, pixel[i]);
            }
        }
    }

    private static int d32ToPcxColor(boolean cleanPixels, int pixel) {
        return switch (pixel) {
            case 0x00000000 -> 0xFF00FFFF;
            case 0x00FF0002 -> 0xFFFF96FF;
            case 0x00FF0001 -> 0xFF00BFBF;
            case 0x00FF0003 -> 0xFFE343C0;
            case 0x00FF0004 -> 0xFFFF00FF;
            case 0x00FF0010 -> 0xFFFFFF00;
            case 0x00FF0014 -> 0xFFB400FF;
            case 0x00FF0012 -> 0xFF00FF00;
            default -> cleanPixels && pixel >>> 24 == 0 ? 0x00000000 : pixel;
        };
    }

    public static void writeBmp(Path path, int[][] pixels) throws IOException {
        int height = pixels.length;
        int width = pixels.length == 0 ? 0 : pixels[0].length;
        int rowSize = (width * 3 + 3) / 4 * 4;  // 3 bytes per pixel in RGB888, round up to multiple of 4
        int imageSize = rowSize * height;

        int fileSize = 14 + 40 + imageSize;

        try (SeekableByteChannel channel = Files.newByteChannel(
                path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
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
                    int color = pixels[y][x];
                    byteBuffer.put((byte)(color >>>  0));
                    byteBuffer.put((byte)(color >>>  8));
                    byteBuffer.put((byte)(color >>> 16));
                }
                for (int i = x * 3; i < rowSize; i++) {
                    byteBuffer.put((byte)0);
                }
            }
            byteBuffer.flip();
            channel.write(byteBuffer);
        }
    }

    public static int[][] loadD32Frame(Path path) {
        Image image = new Image(path.toAbsolutePath().toUri().toString());
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[][] result = new int[height][width];
        for (int i = 0; i < height; i++) {
            image.getPixelReader().getPixels(0, i, width, 1, WritablePixelFormat.getIntArgbInstance(), result[i], 0, 0);
        }

        pcxToD32Colors(result);
        return result;
    }

    private static int diff2(int a, int b) {
        return (a - b) * (a - b);
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

    interface Processor<R> {
        R process(ByteBuffer buffer) throws IOException;
        default R process(FileChannel channel, ByteBuffer buffer) throws IOException {
            return process(buffer);
        }
    }
}
