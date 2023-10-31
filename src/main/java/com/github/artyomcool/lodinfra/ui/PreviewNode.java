package com.github.artyomcool.lodinfra.ui;

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

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import static com.github.artyomcool.lodinfra.ui.ImgFilesUtils.*;

public class PreviewNode extends StackPane {

    private final Executor executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "preview node thread " + PreviewNode.this.hashCode());
        thread.setDaemon(true);
        return thread;
    });  // TODO

    Timeline timeline = new Timeline();

    private final ImageView imageView = new ImageView();
    private List<Image> images = Collections.emptyList();

    {
        imageView.setSmooth(false);
    }
    private Path file;
    private Future<?> previousLoad = CompletableFuture.completedFuture(null);

    private Function<Image, Image> imageProcessor = Function.identity();

    public void show(Path file) {
        if (Objects.equals(this.file, file)) {
            return;
        }

        this.file = file;

        reload();
    }

    private void reload() {
        Path file = this.file;
        previousLoad.cancel(true);

        timeline.stop();
        timeline.getKeyFrames().clear();
        imageView.setImage(null);
        getChildren().setAll(new Label("Loading..."));

        previousLoad = CompletableFuture.runAsync(() -> apply(load(file)), executor);
    }

    public void show(Path fileA, Path fileB) {
        this.file = null;

        previousLoad.cancel(true);

        timeline.stop();
        timeline.getKeyFrames().clear();
        imageView.setImage(null);
        getChildren().setAll(new Label("Loading..."));

        previousLoad = CompletableFuture.runAsync(() -> applyDiff(load(fileA), load(fileB)), executor);
    }

    public Collection<Image> load(Path file) {
        if (file == null) {
            return Collections.emptyList();
        }
        String fileName = file.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".png") || fileName.endsWith(".bmp")) {
            return Collections.singletonList(new Image(file.toAbsolutePath().toUri().toString()));
        } else if (fileName.endsWith(".def")) {
            return loadDef(file).values();
        } else if (fileName.endsWith(".p32")) {
            return Collections.singletonList(loadP32(file));
        } else if (fileName.endsWith(".d32")) {
            return loadD32(file);
        } else {
            return Collections.emptyList();
        }
    }

    private void apply(Collection<Image> images) {
        applyImpl(new ArrayList<>(images));
    }

    private void applyImpl(List<Image> images) {
        images.replaceAll(t -> imageProcessor.apply(t));
        Platform.runLater(() -> {
            this.images = images;
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

    private void applyDiff(Collection<Image> a, Collection<Image> b) {
        List<Image> la = new ArrayList<>(a);
        List<Image> lb = new ArrayList<>(b);
        int maxSize = Math.max(a.size(), b.size());

        List<Image> result = new ArrayList<>(maxSize);
        for (int i = 0; i < maxSize; i++) {
            result.add(diff(la.size() > i ? la.get(i) : null, lb.size() > i ? lb.get(i) : null));
        }
        apply(result);
    }

    private Image diff(Image a, Image b) {
        if (a == null) {
            a = new WritableImage(1, 1);
        }
        if (b == null) {
            b = new WritableImage(1, 1);
        }

        int mw = (int) Math.max(a.getWidth(), b.getWidth());
        int mh = (int) Math.max(a.getHeight(), b.getHeight());

        WritableImage result = new WritableImage(mw, mh);
        for (int y = 0; y < mh; y++) {
            for (int x = 0; x < mw; x++) {
                int ac = a.getPixelReader().getArgb(x, y);
                int bc = b.getPixelReader().getArgb(x, y);

                int diff = colorDifference(ac, bc);

                result.getPixelWriter().setArgb(x, y, diff == 0 ? 0xff00ffff : (0xffff0000 + diff));
            }
        }
        return result;
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

    public void postProcess(Function<Image, Image> imageProcessor) {
        this.imageProcessor = imageProcessor;
        reload();
    }

    public void showFrame(int frame) {
        if (frame == -1) {
            timeline.play();
        } else {
            timeline.stop();
            imageView.setImage(images.get(frame));
        }
    }

}
