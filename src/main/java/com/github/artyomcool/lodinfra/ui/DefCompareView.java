package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.h3common.D32;
import com.github.artyomcool.lodinfra.h3common.DefInfo;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.github.artyomcool.lodinfra.ui.ImgFilesUtils.colorDifference;

public class DefCompareView extends VBox {
    private final AnimationTimer timer = new AnimationTimer() {
        long prev = 0;

        @Override
        public void handle(long now) {
            String animationText = animationSpeed.getText();
            int anim;
            try {
                anim = Integer.parseInt(animationText);
            } catch (NumberFormatException ignored) {
                return;
            }
            if (now - prev > anim * 1_000_000L) {
                nextFrame();
                prev = now;
            }
        }
    };
    private final DefView local = new DefView();
    private final DefControl localControl = new DefControl(local);
    private final DefView remote = new DefView();
    private final DefControl remoteControl = new DefControl(remote);
    private final DefView diff = new DefView();
    private final DefControl diffControl = new DefControl(diff);
    private final DefControl allControl = new DefControl(diff, local, remote);
    private final JFXCheckBox lockPreviews = new JFXCheckBox("Lock previews");
    private final TextField animationSpeed = new TextField("200");

    {
        animationSpeed.setPrefColumnCount(3);
    }

    private final JFXButton expand = new JFXButton(null, expandIcon());

    {
        expand.setPadding(new Insets(4, 4, 4, 4));
    }

    private List<Boolean> changes = new ArrayList<>();

    public DefCompareView() {
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
    }

    public void setImages(Path local, Path remote) {
        DefInfo localDef = load(local);
        DefInfo remoteDef = load(remote);
        changes = new ArrayList<>();
        DefInfo diffDef = makeDiff(localDef, remoteDef, changes);
        diffControl.setHeatmap(changes);
        allControl.setHeatmap(changes);

        this.local.setDef(localDef);
        this.remote.setDef(remoteDef);
        this.diff.setDef(diffDef);
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    private void nextFrame() {
        if (lockPreviews.isSelected()) {
            allControl.tick();
        } else {
            localControl.tick();
            remoteControl.tick();
            diffControl.tick();
        }
    }

    private DefInfo load(Path file) {
        if (file == null) {
            return null;
        }
        if (!Files.exists(file)) {
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

    private DefInfo makeDiff(DefInfo one, DefInfo two, List<Boolean> outChanges) {
        if (one == null) {
            one = new DefInfo();
        }
        if (two == null) {
            two = new DefInfo();
        }
        DefInfo result = new DefInfo();
        result.type = D32.TYPE;
        result.fullWidth = Math.max(one.fullWidth, two.fullWidth);
        result.fullHeight = Math.max(one.fullHeight, two.fullHeight);

        int oneSize = one.groups.size();
        int twoSize = two.groups.size();
        for (int oneGroupIndex = 0, twoGroupIndex = 0, gid = 0; ; gid++) {
            DefInfo.Group oneGroup = oneSize > oneGroupIndex ? one.groups.get(oneGroupIndex) : null;
            DefInfo.Group twoGroup = twoSize > twoGroupIndex ? two.groups.get(twoGroupIndex) : null;
            if (oneGroup == null && twoGroup == null) {
                break;
            }
            if (oneGroup != null && oneGroup.groupIndex != gid) {
                oneGroup = null;
            }
            if (twoGroup != null && twoGroup.groupIndex != gid) {
                twoGroup = null;
            }
            if (oneGroup == null && twoGroup == null) {
                continue;
            }
            if (oneGroup != null) {
                oneGroupIndex++;
            }
            if (twoGroup != null) {
                twoGroupIndex++;
            }

            int oneFrames = oneGroup == null ? 0 : oneGroup.frames.size();
            int twoFrames = twoGroup == null ? 0 : twoGroup.frames.size();
            DefInfo.Group group = new DefInfo.Group(result);
            group.groupIndex = gid;
            for (int j = 0; j < Math.max(oneFrames, twoFrames); j++) {
                DefInfo.Frame oneFrame = oneFrames > j ? oneGroup.frames.get(j) : null;
                DefInfo.Frame twoFrame = twoFrames > j ? twoGroup.frames.get(j) : null;
                DefInfo.Frame diffFrame = new DefInfo.Frame(group);
                int frameIndex = outChanges.size();
                outChanges.add(false);
                diffFrame.data = () -> {
                    int[][] onePixels = oneFrame == null ? new int[0][0] : oneFrame.data.decodeFrame();
                    int[][] twoPixels = twoFrame == null ? new int[0][0] : twoFrame.data.decodeFrame();
                    int height = Math.max(onePixels.length, twoPixels.length);
                    int width = height == 0 ? 0 : Math.max(
                            onePixels.length == 0 ? 0 : onePixels[0].length,
                            twoPixels.length == 0 ? 0 : twoPixels[0].length
                    );
                    int[][] resPixels = new int[height][width];
                    for (int y = 0; y < height; y++) {
                        int[] oneScan = onePixels.length > y ? onePixels[y] : new int[0];
                        int[] twoScan = twoPixels.length > y ? twoPixels[y] : new int[0];
                        int[] resScan = resPixels[y];
                        for (int x = 0; x < width; x++) {
                            int d = colorDifference(
                                    oneScan.length > x ? oneScan[x] : 0,
                                    twoScan.length > x ? twoScan[x] : 0
                            );
                            if (d != 0) {
                                Platform.runLater(() -> notifyChanges(outChanges, frameIndex));
                            }
                            if (d == 0) {
                                d = 0xff00ffff;
                            } else if (d < 0x10) {
                                d = 256 / 16 * d;
                                d = 0xff000000 | d << 8;
                            } else if (d < 0x50) {
                                d = 256 / 0x40 * (d - 0x10);
                                d = 0xff00ff00 | d << 16;
                            } else if (d < 0x90) {
                                d = 256 / 0x40 * (d - 0x50);
                                d = 0xffff0000 | (255 - d) << 8;
                            } else {
                                d = (d - 0x90) * 2;
                                d = 0xffff0000 | d;
                            }
                            resScan[x] = d;
                        }
                    }
                    return resPixels;
                };
                group.frames.add(diffFrame);
            }
            result.groups.add(group);
        }
        return result;
    }

    private void notifyChanges(List<Boolean> changes, int frameIndex) {
        if (this.changes != changes) {
            return;
        }
        changes.set(frameIndex, true);
        diffControl.setHeatmap(changes);
        allControl.setHeatmap(changes);
    }

    private static Node expandIcon() {
        String d = "M175.445,336.555c-5-5.009-13.099-5.009-18.099,0L25.6,468.301V396.8c0-7.074-5.726-12.8-12.8-12.8 C5.726,384,0,389.726,0,396.8v102.4c0,7.074,5.726,12.8,12.8,12.8h102.4c7.074,0,12.8-5.726,12.8-12.8 c0-7.074-5.726-12.8-12.8-12.8H43.699l131.746-131.746C180.446,349.653,180.446,341.555,175.445,336.555z M499.2,0H396.8C389.726,0,384,5.726,384,12.8c0,7.074,5.726,12.8,12.8,12.8h71.492L336.555,157.346 c-5.001,5.001-5.001,13.099,0,18.099c5,5.001,13.099,5.001,18.099,0L486.4,43.699V115.2c0,7.074,5.726,12.8,12.8,12.8 c7.074,0,12.8-5.726,12.8-12.8V12.8C512,5.726,506.274,0,499.2,0z";
        SVGPath path = new SVGPath();
        path.setFill(Color.grayRgb(0x60));
        path.setContent(d);
        path.setScaleX(1d / 32);
        path.setScaleY(1d / 32);
        return new Group(path);
    }

}
