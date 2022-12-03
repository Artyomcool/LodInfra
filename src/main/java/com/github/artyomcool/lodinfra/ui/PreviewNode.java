package com.github.artyomcool.lodinfra.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class PreviewNode extends StackPane {
    Timeline timeline = new Timeline();

    private final ImageView imageView = new ImageView();
    private Path file;
    private Future<?> previousLoad = CompletableFuture.completedFuture(null);

    public void show(Path file) {
        if (Objects.equals(this.file, file)) {
            return;
        }

        this.file = file;
        String fileName = file.getFileName().toString();

        previousLoad.cancel(true);

        timeline.stop();
        timeline.getKeyFrames().clear();

        if (fileName.endsWith(".png") || fileName.endsWith(".bmp")) {
            previousLoad = CompletableFuture.runAsync(() -> {
                Image image = new Image(file.toAbsolutePath().toUri().toString());
                Platform.runLater(() -> {
                    getChildren().setAll(imageView);
                    imageView.setImage(image);
                });
            });
        } else if (fileName.endsWith(".def")) {
            previousLoad = CompletableFuture.runAsync(() -> {
                List<Image> loaded = loadDef(file);
                if (!loaded.isEmpty()) {
                    Platform.runLater(() -> {
                        getChildren().setAll(imageView);
                        animate(file, loaded);
                    });
                }
            });
        } else {
            imageView.setImage(null);
            getChildren().setAll();
        }
    }

    private List<Image> loadDef(Path file) {
        try (FileChannel channel = FileChannel.open(file)) {
            List<Image> result = new ArrayList<>();

            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            buffer.order(ByteOrder.LITTLE_ENDIAN);

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

            for (int i = 0; i < groupCount; i++) {
                int groupType = buffer.getInt();
                int framesCount = buffer.getInt();
                buffer.getInt();
                buffer.getInt();

                names = new String[framesCount];
                for (int j = 0; j < framesCount; j++) {
                    buffer.get(name);
                    int q = 0;
                    while (name[q] != 0) {
                        q++;
                    }
                    names[j] = new String(name, 0, q);
                }

                offsets = new int[framesCount];
                for (int j = 0; j < framesCount; j++) {
                    offsets[j] = buffer.getInt();
                }

                Map<String, Image> deduplication = new HashMap<>();
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
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private Image decode(MappedByteBuffer buffer, int[] palette, int offset) {
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
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                image.getPixelWriter().setArgb(i, j, palette[0]);
            }
        }

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
        }

        return image;
    }

    private void animate(Path file, List<Image> loaded) {
        if (this.file != file) {
            return;
        }

        // fixme!!! leaks
        SimpleIntegerProperty frameIndex = new SimpleIntegerProperty() {
            @Override
            protected void invalidated() {
                imageView.setImage(loaded.get(get()));
            }
        };
        for (int i = 0; i < loaded.size(); i++) {
            timeline.getKeyFrames().add(new KeyFrame(
                    Duration.seconds(0.25 * i),
                    new KeyValue(frameIndex, i, Interpolator.DISCRETE)
            ));
        }
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(0.25 * loaded.size())));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

}
