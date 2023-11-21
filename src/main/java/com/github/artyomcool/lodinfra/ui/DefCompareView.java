package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.h3common.D32;
import com.github.artyomcool.lodinfra.h3common.DefInfo;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.github.artyomcool.lodinfra.ui.ImgFilesUtils.colorDifference;

public class DefCompareView extends VBox {
    private final DefView local = new DefView();
    private final DefControl localControl = new DefControl(local);
    private final DefView remote = new DefView();
    private final DefControl remoteControl = new DefControl(remote);
    private final DefView diff = new DefView();
    private final DefControl diffControl = new DefControl(diff);
    private final DefControl allControl = new DefControl(diff, local, remote);
    private final JFXCheckBox lockPreviews = new JFXCheckBox("Lock previews");
    private final AnimationSpeedField animationSpeed = new AnimationSpeedField(this::nextFrame);

    private final JFXButton expand = new JFXButton(null, expandIcon());

    {
        expand.setPadding(new Insets(4, 4, 4, 4));
        expand.setOnAction(e -> expand());
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

        expand.setVisible(local != null);
    }

    public void start() {
        animationSpeed.start();
    }

    public void stop() {
        animationSpeed.stop();
    }

    public void expand() {
        DefPane root = new DefPane();
        root.setDef(local.getDef(), local.getCurrentFrame());
        Stage stage = new Stage();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("View & Edit");
        stage.setWidth(800);
        stage.setHeight(600);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(getScene().getWindow());
        root.start();
        animationSpeed.stop();
        stage.showAndWait();
        root.stop();
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
                int frameIndex = outChanges.size();
                outChanges.add(false);
                DefInfo.FrameData frameData = () -> {
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
                DefInfo.Frame diffFrame = new DefInfo.Frame(group, frameData);
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
        String d = "M435.197,153.593h-115.2v25.6h102.4v307.2h-332.8v-307.2h102.4v-25.6h-115.2c-7.066,0-12.8,5.734-12.8,12.8v332.8 c0,7.066,5.734,12.8,12.8,12.8h358.4c7.066,0,12.8-5.734,12.8-12.8v-332.8C447.997,159.328,442.262,153.593,435.197,153.593z M341.74,78.782l-76.8-75.136c-5.043-4.941-13.158-4.847-18.099,0.205l-76.595,74.923 c-5.052,4.949-5.146,13.047-0.205,18.108c4.941,5.035,13.056,5.129,18.099,0.188l55.057-53.854v275.098 c0,7.074,5.734,12.8,12.8,12.8c7.066,0,12.8-5.717,12.8-12.8V43.215l55.049,53.854c5.043,4.949,13.158,4.855,18.099-0.188 C346.885,91.821,346.8,83.722,341.74,78.782z";
        SVGPath path = new SVGPath();
        path.setFill(Color.grayRgb(0x60));
        path.setContent(d);
        path.setScaleX(1d / 32);
        path.setScaleY(1d / 32);
        return new Group(path);
    }

}
