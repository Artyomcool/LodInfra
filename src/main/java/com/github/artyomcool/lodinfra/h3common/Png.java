package com.github.artyomcool.lodinfra.h3common;

import ar.com.hjg.pngj.*;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class Png extends DefInfo {

    public static DefInfo load(ByteBuffer buffer) {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        PngReader pngReader = new PngReader(new ByteArrayInputStream(data));
        PngChunkPLTE plte = pngReader.getMetadata().getPLTE();

        DefInfo def = new DefInfo();
        def.type = pngReader.imgInfo.alpha ? P32.TYPE : (plte == null ? Pcx.TYPE24 : Pcx.TYPE8);
        def.fullWidth = pngReader.imgInfo.cols;
        def.fullHeight = pngReader.imgInfo.rows;
        if (plte != null) {
            def.palette = new int[256];
            for (int i = 0; i < plte.getNentries(); i++) {
                def.palette[i] = plte.getEntry(i);
            }
        }

        int[][] pixels = new int[def.fullHeight][def.fullWidth];

        IImageLineSet<? extends IImageLine> rows = pngReader.readRows();
        for (int row = 0; row < rows.size(); row++) {
            IImageLine line = rows.getImageLine(row);
            ImageLineByte lineByte = line instanceof ImageLineByte ? (ImageLineByte) line : null;
            ImageLineInt lineInt = line instanceof ImageLineInt ? (ImageLineInt) line : null;

            int[] scanline = pixels[row];
            for (int column = 0; column < pngReader.imgInfo.cols; column++) {
                if (plte == null) {
                    int pixelARGB8 = ImageLineHelper.getPixelARGB8(line, column);
                    scanline[column] = pixelARGB8;
                } else {
                    scanline[column] = def.palette[lineByte != null ? lineByte.getElem(column) : lineInt.getElem(column) & 0xff];
                }
            }
        }

        Group group = new Group(def);
        Frame frame = new Frame(group);
        frame.data = () -> pixels;
        group.frames.add(frame);
        def.groups.add(group);

        return def;
    }

    public static ByteBuffer pack(Frame frame) {
        DefInfo def = frame.group.def;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean alpha = def.type == D32.TYPE || def.type == P32.TYPE;
        ImageInfo header = new ImageInfo(def.fullWidth, def.fullHeight, 8, alpha, false, def.palette != null);
        PngWriter pngWriter = new PngWriter(out, header);

        int[][] pixels = def.groups.get(0).frames.get(0).data.decodeFrame();
        for (int y = 0; y < def.fullHeight; y++) {
            ImageLineByte line = new ImageLineByte(header);
            int[] scanline = pixels[y];
            for (int x = 0; x < def.fullWidth; x++) {
                int color = scanline[x];
                line.getScanline()[x * 4] = (byte) (color >>> 0);
                line.getScanline()[x * 4 + 1] = (byte) (color >>> 8);
                line.getScanline()[x * 4 + 2] = (byte) (color >>> 16);
                line.getScanline()[x * 4 + 3] = (byte) (color >>> 24);
            }
            pngWriter.writeRow(line);
        }
        pngWriter.end();

        return ByteBuffer.wrap(out.toByteArray());
    }

}
