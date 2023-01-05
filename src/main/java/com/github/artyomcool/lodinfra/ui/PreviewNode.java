package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.LodFile;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.artyomcool.lodinfra.ui.ImgFilesUtils.*;

public class PreviewNode extends StackPane {

    private final Executor executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "preview node thread " + PreviewNode.this.hashCode());
        thread.setDaemon(true);
        return thread;
    });  // TODO

    Timeline timeline = new Timeline();

    private final ImageView imageView = new ImageView();
    private Path file;
    private Future<?> previousLoad = CompletableFuture.completedFuture(null);

    public void show(Path file) {
        if (Objects.equals(this.file, file)) {
            return;
        }

        this.file = file;
        String fileName = file.getFileName().toString().toLowerCase();

        previousLoad.cancel(true);

        timeline.stop();
        timeline.getKeyFrames().clear();
        imageView.setImage(null);
        getChildren().setAll(new Label("Loading..."));

        if (fileName.endsWith(".png") || fileName.endsWith(".bmp")) {
            previousLoad = CompletableFuture.runAsync(() -> apply(new Image(file.toAbsolutePath().toUri().toString())), executor);
        } else if (fileName.endsWith(".def")) {
            previousLoad = CompletableFuture.runAsync(() -> apply(loadDef(file)), executor);
        } else if (fileName.endsWith(".p32")) {
            previousLoad = CompletableFuture.runAsync(() -> apply(loadP32(file)), executor);
        } else if (fileName.endsWith(".d32")) {
            previousLoad = CompletableFuture.runAsync(() -> apply(loadD32(file)), executor);
        } else {
            apply(Collections.emptyList());
        }
    }

    private void apply(Image image) {
        apply(image == null ? Collections.emptyList() : Collections.singletonList(image));
    }

    private void apply(List<Image> images) {
        Platform.runLater(() -> {
            if (images.isEmpty()) {
                getChildren().clear();
                return;
            }

            getChildren().setAll(imageView);
            imageView.setImage(images.get(0));

            if (images.size() == 1) {
                return;
            }
            animate(file, images);
        });
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
