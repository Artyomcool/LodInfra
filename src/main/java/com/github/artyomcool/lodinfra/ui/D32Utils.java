package com.github.artyomcool.lodinfra.ui;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class D32Utils {

    public static List<Image> loadD32(Path file) {
        try (FileChannel channel = FileChannel.open(file)) {
            List<Image> result = new ArrayList<>();

            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
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
        } catch (NoSuchFileException e) {
            return Collections.emptyList();
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        } catch (RuntimeException|Error e) {
            e.printStackTrace();
            throw e;
        }
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

}
