package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.h3common.Def;
import com.github.artyomcool.lodinfra.h3common.LodFile;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;

public class ImgFilesUtils {

    public static List<Image> loadD32(Path file) {
        return processFile(file, Collections.emptyList(), buffer -> {
            List<Image> result = loadD32(buffer);

            return result;
        });
    }

    private static List<Image> loadD32(ByteBuffer buffer) {
        List<Image> result = new ArrayList<>();
        int type = buffer.getInt();
        int version = buffer.getInt();
        int headerSize = buffer.getInt();
        int fullWidth = buffer.getInt();
        int fullHeight = buffer.getInt();
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

            buffer.position(buffer.position() + 13 * framesCount);

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

                WritableImage image = new WritableImage(width, height);

                for (int y = nonZeroColorHeight + nonZeroColorTop - 1; y >= nonZeroColorTop; y--) {
                    for (int x = nonZeroColorLeft; x < nonZeroColorLeft + nonZeroColorWidth; x++) {
                        image.getPixelWriter().setArgb(x, y, buffer.getInt());
                    }
                }

                result.add(image);
            }
            position += framesCount * 4;
        }
        return result;
    }

    public static void clearD32Pixels(Path file) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            List<Image> result = new ArrayList<>();

            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size());
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            int type = buffer.getInt();
            int version = buffer.getInt();
            int headerSize = buffer.getInt();
            int fullWidth = buffer.getInt();
            int fullHeight = buffer.getInt();
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

                buffer.position(buffer.position() + 13 * framesCount);

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

                    for (int y = nonZeroColorHeight + nonZeroColorTop - 1; y >= nonZeroColorTop; y--) {
                        for (int x = nonZeroColorLeft; x < nonZeroColorLeft + nonZeroColorWidth; x++) {
                            if ((buffer.getInt() & 0xFF000000) == 0) {
                                buffer.position(buffer.position() - 4);
                                buffer.putInt(0);
                            }
                        }
                    }
                }
                position += framesCount * 4;
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException(e);
        } catch (RuntimeException|Error e) {
            e.printStackTrace();
            throw e;
        }
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

    private static <T> T processFile(Path file, T def, ImgFilesUtils.Processor<T> processor) {
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
                try (FileChannel channel = FileChannel.open(file)) {
                    MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    return processor.process(buffer);
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

    public static Image decodeDefFrame(ByteBuffer buffer, int[] palette, int offset) {
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
                            image.getPixelWriter().setArgb(xx, yy, palette[index == 0xff ? (buffer.get() & 0xff) : index]);
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
                            image.getPixelWriter().setArgb(xx, yy, palette[index == 0x7 ? (buffer.get() & 0xff) : index]);
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
                            image.getPixelWriter().setArgb(xx, yy, palette[index == 0x7 ? (buffer.get() & 0xff) : index]);
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

    public static void validateDef(Path path, Pattern skipFrames) {
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

                    int groupIndex = i;
                    int frameIndex = j;
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

    interface Processor<R> {
        R process(ByteBuffer buffer) throws IOException;
    }
}
