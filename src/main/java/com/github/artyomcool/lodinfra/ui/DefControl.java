package com.github.artyomcool.lodinfra.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXToggleNode;
import com.jfoenix.skins.JFXSliderSkin;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Skin;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefControl extends HBox {
    private final DefView imageView;
    private final DefView[] dependent;
    private final JFXButton prevFrame = new JFXButton(null, prevIcon());
    public final JFXToggleNode playPause = new JFXToggleNode();
    private List<Boolean> changes = Collections.emptyList();

    {
        playPause.setGraphic(playIcon());
        playPause.setSelected(true);
    }

    private final JFXButton nextFrame = new JFXButton(null, nextIcon());
    private final JFXButton nextDiffFrame = new JFXButton(null, ffIcon());
    public final JFXSlider slider = new JFXSlider() {
        {
            setIndicatorPosition(IndicatorPosition.LEFT);
            valueProperty().addListener(new ChangeListener<>() {
                @Override
                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                    if (!sliderChanging) {
                        imageView.setCurrentIndex(newValue.intValue());
                        playPause.setSelected(false);
                    }
                }
            });
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new JFXSliderSkin(this) {
                StackPane track;
                StackPane thumb;
                {
                    getChildren().add(0, heatmap);
                    heatmap.setSmooth(false);

                    track = (StackPane) getSkinnable().lookup(".track");
                    thumb = (StackPane) getSkinnable().lookup(".thumb");
                }

                @Override
                protected void layoutChildren(double x, double y, double w, double h) {
                    super.layoutChildren(x, y, w, h);
                    double tw = thumb.getWidth();
                    heatmap.setFitWidth(w - tw + 0.5);
                    heatmap.setFitHeight(h);
                    heatmap.resizeRelocate(tw / 2, 0, w - tw + 0.5, h);
                }
            };
        }
    };
    private final ImageView heatmap = new ImageView();
    private WritableImage heatmapImg = new WritableImage(1, 1);
    private boolean sliderChanging = false;

    public DefControl(DefView imageView, DefView... dependent) {
        super(2);
        setAlignment(Pos.CENTER_LEFT);
        this.imageView = imageView;
        this.dependent = dependent;

        List<ButtonBase> buttons = Arrays.asList(prevFrame, playPause, nextFrame, nextDiffFrame);
        getChildren().setAll(prevFrame, playPause, nextFrame, nextDiffFrame, slider);
        HBox.setHgrow(slider, Priority.ALWAYS);

        prevFrame.setOnAction(e -> prevFrame());
        nextFrame.setOnAction(e -> nextFrame());
        nextDiffFrame.setOnAction(e -> nextDiffFrame());
        nextDiffFrame.setVisible(false);

        for (ButtonBase button : buttons) {
            button.setPadding(new Insets(4, 4, 4, 4));
        }

        imageView.addOnChangedListener(() -> {
            if (!isVisible()) {
                return;
            }
            for (DefView view : dependent) {
                view.setFrame(imageView.getGroup(), imageView.getFrame());
            }
            sliderChanging = true;
            slider.setValue(Math.max(0, imageView.getGlobalIndex()));
            slider.setMax(Math.max(0, imageView.getMaxIndex()));
            sliderChanging = false;
        });
    }

    public DefControl noDiff() {
        getChildren().remove(nextDiffFrame);
        return this;
    }

    public void tick(boolean lockOnGroup) {
        if (playPause.isSelected()) {
            if (lockOnGroup) {
                imageView.nextFrameInGroup();
            } else {
                imageView.nextFrame();
            }
        }
    }

    private void prevFrame() {
        imageView.prevFrame();
    }

    private void nextFrame() {
        imageView.nextFrame();
    }

    private void nextDiffFrame() {
        int i = imageView.getGlobalIndex();
        int oldI = i;
        do {
            i = (i + 1) % changes.size();
            if (changes.get(i)) {
                break;
            }
        } while (oldI != i);
        imageView.setCurrentIndex(i);
        playPause.setSelected(false);
    }

    private static Node prevIcon() {
        String d = "M345.6,115.2c-7.066,0-12.8,5.734-12.8,12.8v256c0,7.066,5.734,12.8,12.8,12.8c7.066,0,12.8-5.734,12.8-12.8V128 C358.4,120.934,352.666,115.2,345.6,115.2z M256,0C114.62,0,0,114.62,0,256s114.62,256,256,256s256-114.62,256-256S397.38,0,256,0z M256,486.4 C128.956,486.4,25.6,383.044,25.6,256S128.956,25.6,256,25.6S486.4,128.956,486.4,256S383.044,486.4,256,486.4z M303.454,246.946l-128-128c-5-5.001-13.099-5.001-18.099,0c-5.001,5-5.001,13.099,0,18.099L276.301,256L157.346,374.946 c-5.001,5-5.001,13.099,0,18.099c2.5,2.5,5.777,3.746,9.054,3.746s6.554-1.246,9.054-3.746l128-128 C308.454,260.045,308.454,251.947,303.454,246.946z";
        SVGPath path = new SVGPath();
        path.setFill(Color.grayRgb(0x60));
        path.setContent(d);
        path.setScaleX(-1d / 32);
        path.setScaleY(1d / 32);
        return new Group(path);
    }

    private static Node nextIcon() {
        String d = "M345.6,115.2c-7.066,0-12.8,5.734-12.8,12.8v256c0,7.066,5.734,12.8,12.8,12.8c7.066,0,12.8-5.734,12.8-12.8V128 C358.4,120.934,352.666,115.2,345.6,115.2z M256,0C114.62,0,0,114.62,0,256s114.62,256,256,256s256-114.62,256-256S397.38,0,256,0z M256,486.4 C128.956,486.4,25.6,383.044,25.6,256S128.956,25.6,256,25.6S486.4,128.956,486.4,256S383.044,486.4,256,486.4z M303.454,246.946l-128-128c-5-5.001-13.099-5.001-18.099,0c-5.001,5-5.001,13.099,0,18.099L276.301,256L157.346,374.946 c-5.001,5-5.001,13.099,0,18.099c2.5,2.5,5.777,3.746,9.054,3.746s6.554-1.246,9.054-3.746l128-128 C308.454,260.045,308.454,251.947,303.454,246.946z";
        SVGPath path = new SVGPath();
        path.setFill(Color.grayRgb(0x60));
        path.setContent(d);
        path.setScaleX(1d / 32);
        path.setScaleY(1d / 32);
        return new Group(path);
    }

    private static Node playIcon() {
        String d = "M256,0C114.62,0,0,114.62,0,256s114.62,256,256,256s256-114.62,256-256S397.38,0,256,0z M256,486.4 C128.956,486.4,25.6,383.044,25.6,256S128.956,25.6,256,25.6S486.4,128.956,486.4,256S383.044,486.4,256,486.4z M341.854,246.955l-128-128.009c-5.001-5.001-13.099-5.001-18.099,0c-5.001,5-5.001,13.099,0,18.099L314.701,256 L195.746,374.946c-5.001,5-5.001,13.099,0,18.099c2.5,2.509,5.777,3.755,9.054,3.755c3.277,0,6.554-1.246,9.054-3.746l128-128 C346.854,260.053,346.854,251.955,341.854,246.955z";
        SVGPath path = new SVGPath();
        path.setFill(Color.grayRgb(0x60));
        path.setContent(d);
        path.setScaleX(1d / 32);
        path.setScaleY(1d / 32);
        return new Group(path);
    }

    private static Node ffIcon() {
        String d = "M256,0C114.62,0,0,114.62,0,256s114.62,256,256,256s256-114.62,256-256S397.38,0,256,0z M256,486.4 C128.956,486.4,25.6,383.044,25.6,256S128.956,25.6,256,25.6S486.4,128.956,486.4,256S383.044,486.4,256,486.4z M405.854,246.946l-128-128c-5.001-5.001-13.099-5.001-18.099,0c-5.001,5-5.001,13.099,0,18.099L378.701,256 L259.746,374.946c-5.001,5-5.001,13.099,0,18.099c2.5,2.5,5.777,3.746,9.054,3.746c3.277,0,6.554-1.246,9.054-3.746l128-128 C410.854,260.045,410.854,251.947,405.854,246.946z M303.454,246.946l-128-128c-5-5.001-13.099-5.001-18.099,0c-5.001,5-5.001,13.099,0,18.099L276.301,256L157.346,374.946 c-5.001,5-5.001,13.099,0,18.099c2.5,2.5,5.777,3.746,9.054,3.746s6.554-1.246,9.054-3.746l128-128 C308.454,260.045,308.454,251.947,303.454,246.946z";
        SVGPath path = new SVGPath();
        path.setFill(Color.grayRgb(0x60));
        path.setContent(d);
        path.setScaleX(1d / 32);
        path.setScaleY(1d / 32);
        return new Group(path);
    }

    public void setHeatmap(List<Boolean> changes) {
        this.changes = changes;
        slider.setVisible(changes.size() > 1);
        if (heatmapImg.getWidth() != changes.size()) {
            heatmapImg = new WritableImage(Math.max(1, changes.size()), 1);
        }
        boolean hasChange = false;
        int x = 0;
        for (Boolean change : changes) {
            if (change) {
                hasChange = true;
            }
            heatmapImg.getPixelWriter().setArgb(x++, 0, change ? 0xffff0000 : 0x00ff0000);
        }

        heatmap.setImage(heatmapImg);
        nextDiffFrame.setVisible(changes.size() > 1 && hasChange);
    }
}
