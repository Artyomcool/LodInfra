package com.github.artyomcool.lodinfra.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.control.TextField;

public class AnimationSpeedField extends TextField {

    private final Runnable callback;

    private final AnimationTimer timer = new AnimationTimer() {
        long prev = 0;

        @Override
        public void handle(long now) {
            String animationText = getText();
            int anim;
            try {
                anim = Integer.parseInt(animationText);
            } catch (NumberFormatException ignored) {
                return;
            }
            if (now - prev > anim * 1_000_000L) {
                callback.run();
                prev = now;
            }
        }
    };

    public AnimationSpeedField(Runnable callback) {
        super("200");
        setPrefColumnCount(3);

        this.callback = callback;
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

}
