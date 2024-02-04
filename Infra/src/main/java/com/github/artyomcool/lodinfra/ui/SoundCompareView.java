package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.Resource;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Properties;

public class SoundCompareView extends VBox {

    private final SndView localView = new SndView();
    private final SndView remoteView = new SndView();
    private final Properties cfg;

    public SoundCompareView(Properties cfg) {
        this.cfg = cfg;
    }

    public boolean applySound(Path local, Path remote) {
        Path lod = Resource.pathOfLod(local);
        if (lod != null) {
            if (!lod.getFileName().toString().toLowerCase().endsWith(".snd")) {
                return false;
            }
        } else if (!local.getFileName().toString().toLowerCase().endsWith(".wav")) {
            return false;
        }


        return false;
    }
}
