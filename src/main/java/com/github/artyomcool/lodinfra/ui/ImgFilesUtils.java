package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.LodFile;
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
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

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

    static List<Image> loadDef(Path file) {
        return processFile(file, Collections.emptyList(), buffer -> {
            Map<String, Image> deduplication = new HashMap<>();
            List<Image> result = new ArrayList<>();

            int type = buffer.getInt();
            int fullWidth = buffer.getInt();
            int fullHeight = buffer.getInt();

            int groupCount = buffer.getInt();
            int[] palette = new int[256];
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

            byte[] name = new byte[13];
            String[] names;
            int[] offsets;

            int position = buffer.position();
            for (int i = 0; i < groupCount; i++) {
                buffer.position(position);
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
                        throw new RuntimeException("Strange def name " + new String(name), e);
                    }
                }

                offsets = new int[framesCount];
                for (int j = 0; j < framesCount; j++) {
                    offsets[j] = buffer.getInt();
                }

                position = buffer.position();

                for (int p = 0; p < framesCount; p++) {
                    String n = names[p];
                    Image image = deduplication.get(n);
                    if (image == null) {
                        image = decode(buffer, palette, offsets[p]);
                        deduplication.put(n, image);
                    }
                    result.add(image);
                }
            }

            return result;
        });
    }

    private static <T> T processFile(Path file, T def, ImgFilesUtils.Processor<T> processor) {
        try {
            String s = file.getFileName().toString();
            if (s.contains("?")) {
                Path p = file.resolveSibling(s.substring(0, s.indexOf("?")));
                try (FileChannel channel = FileChannel.open(p)) {
                    MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    LodFile lod = LodFile.parse(buffer);
                    String name = s.substring(s.indexOf("?") + 1);
                    for (LodFile.SubFileMeta subFile : lod.subFiles) {
                        if (name.equals(new String(subFile.name).trim())) {
                            if (subFile.compressedSize != 0) {
                                ByteBuffer uncompressed = ByteBuffer.allocate(subFile.uncompressedSize);
                                Inflater inflater = new Inflater();
                                inflater.setInput(
                                        lod.originalData.slice(subFile.globalOffsetInFile, subFile.compressedSize)
                                );
                                try {
                                    inflater.inflate(uncompressed);
                                } catch (DataFormatException ex) {
                                    throw new IOException(ex);
                                }
                                inflater.end();

                                uncompressed.rewind();
                                uncompressed.order(ByteOrder.LITTLE_ENDIAN);
                                return processor.process(uncompressed);
                            } else {
                                return processor.process(
                                        lod.originalData.slice(subFile.globalOffsetInFile, subFile.uncompressedSize)
                                );
                            }
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

    private static Image decode(ByteBuffer buffer, int[] palette, int offset) {
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

    public static void validateDef(Path path) {
        processFile(path, null, buffer -> {
            int type = buffer.getInt();
            int fullWidth = buffer.getInt();
            int fullHeight = buffer.getInt();

            int groupCount = buffer.getInt();
            int[] palette = new int[256];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = 0xff000000 | (buffer.get() & 0xff) << 16 | (buffer.get() & 0xff) << 8 | (buffer.get() & 0xff);
            }

            byte[] name = new byte[13];
            String[] names;

            Map<String, String> remap = new LinkedHashMap<>();
            Map<ByteBuffer, String> shouldBeNamed = new LinkedHashMap<>();

            int position = buffer.position();
            for (int i = 0; i < groupCount; i++) {
                buffer.position(position);

                int groupType = buffer.getInt();
                int framesCount = buffer.getInt();
                buffer.getInt();
                buffer.getInt();

                names = new String[framesCount];
                for (int j = 0; j < framesCount; j++) {
                    int namePos = buffer.position();
                    buffer.get(name);
                    try {
                        int q = 0;
                        while (name[q] != 0) {
                            q++;
                        }
                        names[j] = new String(name, 0, q);
                    } catch (IndexOutOfBoundsException e) {
                        names[j] = new String(name);
                        //System.err.println(path + " has wrong name at " + j + "/" + framesCount + " " + new String(name) + " " + LocalDate.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault()));
                    }

                    int groupIndex = i;
                    int frameIndex = j;
                    String old = names[j];
                    String newName = remap.computeIfAbsent(old, k -> {
                        String defName = path.getFileName().toString();
                        defName = defName.substring(0, defName.indexOf("."));
                        if (defName.length() > 8) {
                            defName = defName.substring(0, 4) + defName.substring(defName.length() - 4);
                        }
                        return defName + (char)((int)'A' + groupIndex) + String.format("%03d", frameIndex);
                    });

                    if (!old.equals(newName)) {
                        names[j] = newName;
                        Arrays.fill(name, (byte)0);
                        newName.getBytes(0, newName.length(), name, 0);
                        //buffer.position(namePos).put(name);
                        System.out.println(path.getFileName() + " " + groupIndex + " " + frameIndex + " " + newName + " " + old);
                    }
                }

                int[] offsets = new int[framesCount];
                for (int j = 0; j < framesCount; j++) {
                    offsets[j] = buffer.getInt();   //offset
                }

                position = buffer.position();

                for (int p = 0; p < framesCount; p++) {
                    //String n = names[p];
                    //String was = shouldBeNamed.put(buffer.slice(offsets[p], buffer.getInt(offsets[p])), n);
                    //if (was != null && !was.equals(n)) {
                    //    System.out.println("Exactly same data with different names: " + path + " " + n + " (duplicates " + was + ")");
                    //}
                }
            }

            return null;
        });
    }

    interface Processor<R> {
        R process(ByteBuffer buffer) throws IOException;
    }
}
