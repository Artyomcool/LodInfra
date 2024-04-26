package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.Icons;
import com.github.artyomcool.lodinfra.h3common.DefInfo;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXSlider;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DefCompareView extends VBox {
    private static final Executor LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Preview node thread");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((thread1, throwable) -> throwable.printStackTrace());
        return thread;
    });
    private final DefView local = new DefView();
    private final DefControl localControl = new DefControl(local);
    private final DefView remote = new DefView();
    private final DefControl remoteControl = new DefControl(remote);
    private final DefView diff = new DefView();
    private final DefControl diffControl = new DefControl(diff);
    private final DefControl allControl = new DefControl(diff, local, remote);
    private final JFXCheckBox lockPreviews = new JFXCheckBox("Lock previews");
    private final AnimationSpeedField animationSpeed = new AnimationSpeedField(this::nextFrame);

    private final JFXButton expand = new JFXButton(null, Icons.expandIcon());
    private final Path restore;

    {
        expand.setPadding(new Insets(4, 4, 4, 4));
        expand.setOnAction(e -> expand());
    }

    private Future<?> previousLoad = CompletableFuture.completedFuture(null);
    private Object token = new Object();

    public DefCompareView(Path restore) {
        this.restore = restore;
        setSpacing(2);
        HBox speed = new HBox(new Label("Frame rate: "), animationSpeed);
        speed.setAlignment(Pos.CENTER_LEFT);
        HBox top = new HBox(8, lockPreviews, speed, expand);
        top.setAlignment(Pos.CENTER_LEFT);
        getChildren().setAll(
                top,
                allControl,
                new Label("Local"),
                local,
                localControl,
                new Label("Remote"),
                remote,
                remoteControl,
                new Label("Diff"),
                diff,
                diffControl
        );
        allControl.setViewOrder(-1);
        diffControl.setViewOrder(-1);
        localControl.setViewOrder(-1);
        remoteControl.setViewOrder(-1);

        localControl.setVisible(false);
        remoteControl.setVisible(false);
        diffControl.setVisible(false);

        lockPreviews.setSelected(true);
        lockPreviews.setOnAction(e -> {
            localControl.setVisible(!lockPreviews.isSelected());
            remoteControl.setVisible(!lockPreviews.isSelected());
            diffControl.setVisible(!lockPreviews.isSelected());
            allControl.setVisible(lockPreviews.isSelected());
        });

        allControl.slider.setIndicatorPosition(JFXSlider.IndicatorPosition.RIGHT);
    }

    public void setImages(Path local, Path remote) {
        previousLoad.cancel(false);
        this.local.loading();
        //this.local.setTransformation(Function.identity());
        this.remote.loading();
        this.diff.loading();

        Object token = new Object();
        this.token = token;
        previousLoad = CompletableFuture.runAsync(() -> {
            try {
                DefInfo localDef = load(local);
                DefInfo remoteDef = load(remote);

                DefInfo diffDef = DefInfo.makeDiff(localDef, remoteDef);

                Platform.runLater(() -> {
                    if (token != DefCompareView.this.token) {
                        return;
                    }

                    this.local.setDef(localDef);
                    this.remote.setDef(remoteDef);
                    this.diff.setDef(diffDef);

                    /*if (localDef != null && localDef.mask != null) {
                        this.local.setTransformation(img -> {
                            int width = (int) img.getWidth();
                            int height = (int) img.getHeight();
                            WritableImage res = new WritableImage(width, height);
                            int[] line = new int[width];
                            for (int y = 0; y < height; y++) {
                                img.getPixelReader().getPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), line, 0, width);
                                for (int x = 0; x < line.length; x++) {
                                    boolean dirty = localDef.mask.isDirty(x / 32, y / 32);
                                    boolean shadow = localDef.mask.isShadow(x / 32, y / 32);
                                    if (dirty || shadow) {
                                        int c = line[x];
                                        int r = c >>> 16 & 0xff;
                                        int g = c >>> 8 & 0xff;
                                        int b = c & 0xff;
                                        r = dirty ? (r + 0xff) / 2 : r / 2;
                                        g = g / 2;
                                        b = shadow ? (b + 0xff) / 2 : b / 2;
                                        line[x] = (c & 0xff000000) | r << 16 | g << 8 | b;
                                    }
                                }
                                res.getPixelWriter().setPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), line, 0, width);
                            }
                            return res;
                        });
                    }*/

                    localControl.refreshImage();
                    remoteControl.refreshImage();
                    diffControl.refreshImage();
                    allControl.refreshImage();

                    expand.setVisible(local != null);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, LOADER);

        expand.setVisible(false);
    }

    public void start() {
        animationSpeed.start();
    }

    public void stop() {
        animationSpeed.stop();
    }

    public void expand() {
        DefEditor root = new DefEditor(restore);
        root.setDef(local.getDef(), local.getCurrentFrame());
        animationSpeed.stop();
        root.showModal(getScene());
        animationSpeed.start();
    }

    private void nextFrame() {
        if (lockPreviews.isSelected()) {
            allControl.tick(false);
        } else {
            localControl.tick(false);
            remoteControl.tick(false);
            diffControl.tick(false);
        }
    }

    private DefInfo load(Path file) {
        if (file == null) {
            return null;
        }

        String fileName = file.getFileName().toString().toLowerCase();
        for (String ext : Arrays.asList("png", "bmp", "def", "p32", "d32", "pcx")) {
            if (fileName.endsWith("." + ext)) {
                return DefInfo.load(file);
            }
        }
        return null;
    }

}
