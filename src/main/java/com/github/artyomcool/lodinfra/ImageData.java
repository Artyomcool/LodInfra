package com.github.artyomcool.lodinfra;

public class ImageData {
    final boolean hasPalette;
    final byte[] data;

    public ImageData(boolean hasPalette, byte[] data) {
        this.hasPalette = hasPalette;
        this.data = data;
    }
}
