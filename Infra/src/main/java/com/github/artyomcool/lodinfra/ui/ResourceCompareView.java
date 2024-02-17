package com.github.artyomcool.lodinfra.ui;

import javafx.scene.layout.StackPane;

import java.nio.file.Path;
import java.util.Properties;

public class ResourceCompareView extends StackPane {

    private final DefCompareView defPreview;
    private final SoundCompareView soundCompareView;

    public ResourceCompareView(Properties cfg, Path restorePath) {
        defPreview = new DefCompareView(restorePath);
        soundCompareView = new SoundCompareView(cfg);
        getChildren().add(defPreview);
    }

    public void start() {
        defPreview.start();
    }

    public void stop() {
        defPreview.stop();
        soundCompareView.stop();
    }

    public void show(Path local, Path remote) {
        if (soundCompareView.applySound(local, remote)) {
            return;
        }

        defPreview.setImages(local, remote);
    }
}
