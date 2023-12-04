package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.h3common.DefInfo;
import javafx.scene.image.*;
import javafx.scene.text.Text;

import java.util.*;
import java.util.function.Function;

public class DefView extends ImageView {

    private static final Image LOADING = new Text("Loading").snapshot(null, null);
    private static final Image EMPTY = new Text("Empty").snapshot(null, null);

    private DefInfo def;
    private Runnable onChanged = () -> {
    };

    private final Map<DefInfo.Frame, Image> mapping = new IdentityHashMap<>();
    private final Map<DefInfo.Frame, Image> originalMapping = new IdentityHashMap<>();
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
        mapping.clear();
        originalMapping.clear();
        if (def != null) {
            for (DefInfo.Group group : def.groups) {
                int fi = 0;
                for (DefInfo.Frame frame : group.frames) {
                    int frameGroup = group.groupIndex << 16 | fi++;
                    frameIndexToFameGroup.add(frameGroup);
                    frameGroupToFrameIndex.put(frameGroup, frames.size());
                    frames.add(frame);
                    WritableImage image = new WritableImage(new PixelBuffer<>(frame.fullWidth, frame.fullHeight, frame.pixels, PixelFormat.getIntArgbPreInstance()));
                    mapping.put(frame, image);
                    originalMapping.put(frame, image);
                }
            }
        }

        updateFrame();
    }

    public void setTransformation(Function<Image, Image> transform) {
        for (Map.Entry<DefInfo.Frame, Image> entry : originalMapping.entrySet()) {
            mapping.put(entry.getKey(), transform.apply(entry.getValue()));
        }
        setImage(currentImage());
    }

    public void loading() {
        setDef(null);
        setImage(LOADING);
    }

    public void addOnChangedListener(Runnable runnable) {
        Runnable prev = this.onChanged;
        this.onChanged = () -> {
            prev.run();
            runnable.run();
        };
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

    private void updateFrame() {
        setImage(currentImage());
        onChanged.run();
    }

    public Image currentImage() {
        DefInfo.Frame frame = getCurrentFrame();
        if (frame == null) {
            return EMPTY;
        }
        return mapping.get(frame);
    }

    public DefInfo.Frame getCurrentFrame() {
        return getFrameAtGlobalIndex(globalIndex);
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

    public DefInfo.Frame getFrameAtGlobalIndex(int globalIndex) {
        if (globalIndex < 0 || globalIndex >= frames.size()) {
            return null;
        }
        return frames.get(globalIndex);
    }
}
