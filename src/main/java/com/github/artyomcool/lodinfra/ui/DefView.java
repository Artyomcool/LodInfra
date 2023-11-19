package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.h3common.DefInfo;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DefView extends ImageView {
    public static final Executor EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Preview node thread");
        thread.setDaemon(true);
        return thread;
    });

    private static final Image LOADING = new Text("Loading").snapshot(null, null);
    private static final Image EMPTY = new Text("Empty").snapshot(null, null);

    private DefInfo def;

    private Future<?> previousLoad = CompletableFuture.completedFuture(null);
    private Runnable onChanged = () -> {};

    private final Map<DefInfo.Frame, Image> loaded = new HashMap<>();
    private final List<DefInfo.Frame> frames = new ArrayList<>();
    private final List<Integer> frameIndexToFameGroup = new ArrayList<>();
    private final Map<Integer, Integer> frameGroupToFrameIndex = new HashMap<>();
    private int frameIndex;

    public void setDef(DefInfo def) {
        this.def = def;

        frameIndex = 0;
        loaded.clear();

        frames.clear();
        frameIndexToFameGroup.clear();
        frameGroupToFrameIndex.clear();
        if (def != null) {
            for (DefInfo.Group group : def.groups) {
                int fi = 0;
                for (DefInfo.Frame frame : group.frames) {
                    int frameGroup = group.groupIndex << 16 | fi++;
                    frameIndexToFameGroup.add(frameGroup);
                    frameGroupToFrameIndex.put(frameGroup, frames.size());
                    frames.add(frame);
                }
            }
        }

        reload(def);
        onChanged.run();
    }

    public void addOnChangedListener(Runnable runnable) {
        Runnable prev = this.onChanged;
        this.onChanged = () -> {
            prev.run();
            runnable.run();
        };
    }

    private void reload(DefInfo def) {
        previousLoad.cancel(false);
        if (def != null) {
            setImage(LOADING);
        } else {
            setImage(EMPTY);
            return;
        }
        previousLoad = CompletableFuture.runAsync(() -> {
            Map<DefInfo.Frame, Image> images = new HashMap<>();
            for (DefInfo.Group group : def.groups) {
                for (DefInfo.Frame frame : group.frames) {
                    images.computeIfAbsent(frame, f -> ImgFilesUtils.decode(f.data.decodeFrame(), true, false));
                }
            }

            Platform.runLater(() -> notifyLoaded(def, images));
        }, EXECUTOR);
    }

    public void prevFrame() {
        if (def == null || def.groups.isEmpty()) {
            return;
        }
        frameIndex--;
        if (frameIndex < 0) {
            frameIndex = frames.size() - 1;
        }
        updateFrame();
    }

    public void nextFrame() {
        if (def == null || def.groups.isEmpty()) {
            return;
        }
        frameIndex++;
        if (frameIndex >= frames.size()) {
            frameIndex = 0;
        }
        updateFrame();
    }

    public void setFrame(int group, int frame) {
        frameIndex = frameGroupToFrameIndex.getOrDefault(group << 16 | frame, -1);
        updateFrame();
    }

    private void notifyLoaded(DefInfo def, Map<DefInfo.Frame, Image> images) {
        if (this.def != def) {
            return;
        }
        loaded.clear();
        loaded.putAll(images);
        updateFrame();
    }

    private void updateFrame() {
        setImage(currentImage());
        onChanged.run();
    }

    public Image currentImage() {
        DefInfo.Frame frame = currentFrame();
        if (frame == null) {
            return EMPTY;
        }
        return loaded.get(frame);
    }

    private DefInfo.Frame currentFrame() {
        if (frameIndex < 0 || frameIndex >= frames.size()) {
            return null;
        }
        return frames.get(frameIndex);
    }

    public int getFrame() {
        if (frameIndex < 0 || frameIndex >= frameIndexToFameGroup.size()) {
            return 0;
        }
        return frameIndexToFameGroup.get(frameIndex) & 0xffff;
    }
    public int getGroup() {
        if (frameIndex < 0 || frameIndex >= frameIndexToFameGroup.size()) {
            return 0;
        }
        return frameIndexToFameGroup.get(frameIndex) >> 16;
    }

    public int currentIndex() {
        return frameIndex;
    }

    public void setCurrentIndex(int index) {
        this.frameIndex = index;
        updateFrame();
    }

    public int maxIndex() {
        return frames.size() - 1;
    }
}
