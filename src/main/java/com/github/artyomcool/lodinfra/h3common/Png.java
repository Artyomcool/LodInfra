package com.github.artyomcool.lodinfra.h3common;

import ar.com.hjg.pngj.*;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;
import com.github.artyomcool.lodinfra.ui.ImgFilesUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

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
                def.palette[i] = 0xff000000 | plte.getEntry(i);
            }
        }

        IntBuffer pixels = IntBuffer.allocate(def.fullWidth * def.fullHeight);

        IImageLineSet<? extends IImageLine> rows = pngReader.readRows();
        for (int row = 0; row < rows.size(); row++) {
            IImageLine line = rows.getImageLine(row);
            ImageLineByte lineByte = line instanceof ImageLineByte ? (ImageLineByte) line : null;
            ImageLineInt lineInt = line instanceof ImageLineInt ? (ImageLineInt) line : null;

            for (int column = 0; column < pngReader.imgInfo.cols; column++) {
                if (plte == null) {
                    int pixelARGB8 = ImageLineHelper.getPixelARGB8(line, column);
                    pixels.put(pixelARGB8);
                } else {
                    int color = lineByte != null ? lineByte.getElem(column) : lineInt.getElem(column) & 0xff;
                    pixels.put(def.palette[color]);
                }
            }
        }

        ImgFilesUtils.premultiply(pixels.array());

        Group group = new Group(def);
        Frame frame = new Frame(group, def.fullWidth, def.fullHeight, pixels.flip());
        group.frames.add(frame);
        def.groups.add(group);

        return def;
    }

    // TODO verify palette
    public static ByteBuffer pack(Frame frame) {
        DefInfo def = frame.group.def;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean alpha = def.type == D32.TYPE || def.type == P32.TYPE;
        ImageInfo header = new ImageInfo(frame.fullWidth, frame.fullHeight, 8, alpha, false, def.palette != null);
        PngWriter pngWriter = new PngWriter(out, header);
        if (def.palette != null) {
            Map<Integer, Byte> paletteMap = new HashMap<>();
            PngChunkPLTE chunk = new PngChunkPLTE(header);
            chunk.setNentries(def.palette.length);
            for (int i = 0; i < def.palette.length; i++) {
                int c = def.palette[i];
                chunk.setEntry(i, c >>> 16 & 0xff, c >>> 8 & 0xff, c & 0xff);
                paletteMap.put(c, (byte) i);
            }
            pngWriter.queueChunk(chunk);

            for (int y = 0; y < frame.fullHeight; y++) {
                ImageLineByte line = new ImageLineByte(header);
                for (int x = 0; x < frame.fullWidth; x++) {
                    int color = frame.color(x, y);
                    line.getScanline()[x] = paletteMap.get(color);
                }
                pngWriter.writeRow(line);
            }
        } else {
            for (int y = 0; y < frame.fullHeight; y++) {
                //ImageLineByte line = new ImageLineByte(header);
                ImageLineInt line = new ImageLineInt(header);
                for (int x = 0; x < frame.fullWidth; x++) {
                    int color = frame.color(x, y);
                    line.getScanline()[x] = color;
                }
                pngWriter.writeRow(line);
            }
        }
        pngWriter.end();

        return ByteBuffer.wrap(out.toByteArray());
    }

}
