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

    private final Map<int[][], Image> loaded = new HashMap<>();
    private final List<DefInfo.Frame> frames = new ArrayList<>();
    private final List<Integer> frameIndexToFameGroup = new ArrayList<>();
    private final Map<Integer, Integer> frameGroupToFrameIndex = new HashMap<>();
    private int globalIndex;

    public void setDef(DefInfo def) {
        this.def = def;

        globalIndex = 0;

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
            boolean needLoad = false;
            a: for (DefInfo.Group group : def.groups) {
                for (DefInfo.Frame frame : group.frames) {
                    if (frame.cachedData == null) {
                        needLoad = true;
                        break a;
                    }
                    if (!loaded.containsKey(frame.cachedData)) {
                        needLoad = true;
                        break a;
                    }
                }
            }
            if (!needLoad) {
                Map<int[][], Image> images = new HashMap<>();
                for (DefInfo.Group group : def.groups) {
                    for (DefInfo.Frame frame : group.frames) {
                        images.put(frame.cachedData, loaded.get(frame.cachedData));
                    }
                }
                notifyLoaded(def, images);
                return;
            }
            loaded.clear();
            setImage(LOADING);
        } else {
            loaded.clear();
            setImage(EMPTY);
            return;
        }
        previousLoad = CompletableFuture.runAsync(() -> {
            Map<int[][], Image> images = new HashMap<>();
            for (DefInfo.Group group : def.groups) {
                for (DefInfo.Frame frame : group.frames) {
                    images.computeIfAbsent(frame.decodeFrame(), f -> ImgFilesUtils.decode(f, true, false));
                }
            }

            Platform.runLater(() -> notifyLoaded(def, images));
        }, EXECUTOR);
    }

    public void prevFrame() {
        if (def == null || def.groups.isEmpty()) {
            return;
        }
        globalIndex--;
        if (globalIndex < 0) {
            globalIndex = frames.size() - 1;
        }
        updateFrame();
    }

    public void nextFrame() {
        if (def == null || def.groups.isEmpty()) {
            return;
        }
        globalIndex++;
        if (globalIndex >= frames.size()) {
            globalIndex = 0;
        }
        updateFrame();
    }

    public void nextFrameInGroup() {
        if (def == null || def.groups.isEmpty()) {
            return;
        }
        int group = getGroup();
        setFrame(group, getFrame() + 1);
        if (globalIndex == -1) {
            setFrame(group, 0);
        }
        updateFrame();
    }

    public void setFrame(int group, int frame) {
        globalIndex = frameGroupToFrameIndex.getOrDefault(group << 16 | frame, -1);
        updateFrame();
    }

    private void notifyLoaded(DefInfo def, Map<int[][], Image> images) {
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
        DefInfo.Frame frame = getCurrentFrame();
        if (frame == null) {
            return EMPTY;
        }
        return loaded.get(frame.decodeFrame());
    }

    public DefInfo.Frame getCurrentFrame() {
        if (globalIndex < 0 || globalIndex >= frames.size()) {
            return null;
        }
        return frames.get(globalIndex);
    }

    public int getFrame() {
        if (globalIndex < 0 || globalIndex >= frameIndexToFameGroup.size()) {
            return 0;
        }
        return frameIndexToFameGroup.get(globalIndex) & 0xffff;
    }
    public int getGroup() {
        if (globalIndex < 0 || globalIndex >= frameIndexToFameGroup.size()) {
            return 0;
        }
        return frameIndexToFameGroup.get(globalIndex) >> 16;
    }

    public int getGlobalIndex() {
        return globalIndex;
    }

    public void setCurrentIndex(int index) {
        this.globalIndex = index;
        updateFrame();
    }

    public int getMaxIndex() {
        return frames.size() - 1;
    }

    public DefInfo getDef() {
        return def;
    }

    public void setFrame(DefInfo.Frame value) {
        setCurrentIndex(frames.indexOf(value));
    }
}
