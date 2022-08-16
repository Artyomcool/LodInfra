package com.github.artyomcool.lodinfra;

import ar.com.hjg.pngj.*;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ResourceConverter {

    public static ByteBuffer fromPng(String name, ByteBuffer buffer) {
        buffer = buffer.asReadOnlyBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        PngReader pngReader = new PngReader(new ByteArrayInputStream(data));
        PngChunkPLTE plte = pngReader.getMetadata().getPLTE();
        if (name.toLowerCase().endsWith(".idx.png")) {
            if (plte == null) {
                throw new IllegalStateException(name + " is expected to have palette");
            }
        } else {
            if (plte != null) {
                throw new IllegalStateException(name + " doesn't expected to have palette");
            }
        }

        if (pngReader.imgInfo.alpha) {
            throw new IllegalStateException(name + " alpha channel is not supported");
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);

            int size = pngReader.imgInfo.cols * pngReader.imgInfo.rows;

            output.writeInt(Integer.reverseBytes(plte == null ? size * 3 : size));
            output.writeInt(Integer.reverseBytes(pngReader.imgInfo.cols));
            output.writeInt(Integer.reverseBytes(pngReader.imgInfo.rows));

            IImageLineSet<? extends IImageLine> rows = pngReader.readRows();
            for (int row = 0; row < rows.size(); row++) {
                IImageLine line = rows.getImageLine(row);
                ImageLineByte lineByte = line instanceof ImageLineByte ? (ImageLineByte) line : null;
                ImageLineInt lineInt = line instanceof ImageLineInt ? (ImageLineInt) line : null;

                for (int column = 0; column < pngReader.imgInfo.cols; column++) {
                    if (plte == null) {
                        int pixelRGB8 = ImageLineHelper.getPixelRGB8(line, column);
                        output.writeByte((pixelRGB8 >> 0) & 0xff);
                        output.writeByte((pixelRGB8 >> 8) & 0xff);
                        output.writeByte((pixelRGB8 >> 16) & 0xff);
                    } else {
                        output.writeByte(lineByte != null ? lineByte.getElem(column) : lineInt.getElem(column) & 0xff);
                    }
                }
            }
            if (plte != null) {
                for (int i = 0; i < 256; i++) {
                    int entry = plte.getEntry(i);
                    output.writeByte((entry >> 16) & 0xff);
                    output.writeByte((entry >> 8) & 0xff);
                    output.writeByte((entry >> 0) & 0xff);
                }
            }

            return ByteBuffer.wrap(bytes.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ImageData toBmp(byte[] d) {
        ByteBuffer data = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);

        int size = data.getInt();
        int width = data.getInt();
        int height = data.getInt();
        boolean hasPalette = size == width * height;

        int lineSize = hasPalette ? (width * 8 + 31) / 32 * 4 : (width * 3 + 3) / 4 * 4;
        int imageSize = lineSize * height;
        int newSize = 14 + 40 + imageSize + (hasPalette ? 256 * 4 : 0);
        ByteBuffer out = ByteBuffer.allocate(newSize).order(ByteOrder.LITTLE_ENDIAN);

        out.putShort((short) 0x4d42);
        out.putInt(newSize);
        out.putInt(0);
        out.putInt(14 + 40 + 4 * (hasPalette ? 256 : 0));
        out.putInt(40);
        out.putInt(width);
        out.putInt(-height);
        out.putShort((short) 1);
        out.putShort((short) (hasPalette ? 8 : 24));
        out.putInt(0);
        out.putInt(imageSize);
        out.putInt(72);
        out.putInt(72);
        out.putInt(hasPalette ? 256 : 0);
        out.putInt(0);

        if (hasPalette) {
            for (int i = 0; i < 256; i++) {
                int r = data.get(12 + size + i * 3 + 0);
                int g = data.get(12 + size + i * 3 + 1);
                int b = data.get(12 + size + i * 3 + 2);

                out.put((byte) b).put((byte) g).put((byte) r).put((byte) 0xff);
            }
            for (int y = 0; y < height; y++) {
                int x;
                for (x = 0; x < width; x++) {
                    out.put(data.get());
                }
                for (; x < lineSize; x++) {
                    out.put((byte) 0);
                }
            }
        } else {
            for (int y = 0; y < height; y++) {
                int x;
                for (x = 0; x < width; x++) {
                    byte b = data.get();
                    byte g = data.get();
                    byte r = data.get();
                    out.put(b);
                    out.put(g);
                    out.put(r);
                }
                x *= 3;
                for (; x < lineSize; x++) {
                    out.put((byte) 0);
                }
            }
        }

        return new ImageData(hasPalette, out.array());
    }

    public static ByteBuffer fromBMP(String name, ByteBuffer data) {
        data = data.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);

        int magic = data.getShort() & 0xffff;
        if (magic != 0x4D42) {  // BM
            throw new IllegalArgumentException("Invalid signature for " + name);
        }
        int fileSize = data.getInt();
        data.getInt();  // reserved
        int dataOffset = data.getInt();
        int headerSize = data.getInt();
        if (headerSize != 40) {
            throw new IllegalArgumentException("Invalid bmp " + name);
        }
        int width = data.getInt();
        int height = data.getInt();
        int planes = data.getShort() & 0xffff;
        int bpp = data.getShort() & 0xffff;
        if (bpp != 24 && bpp != 8) {
            throw new IllegalArgumentException("Invalid bitmap size");
        }
        int compression = data.getInt();
        if (compression != 0) {
            throw new IllegalArgumentException("Compressed bmp are not supported");
        }
        int imageSize = data.getInt();
        int dpiX = data.getInt();
        int dpiY = data.getInt();
        int colorsUsed = data.getInt();
        int colorsImportant = data.getInt();

        if (colorsUsed == 0 && bpp == 8) {
            colorsUsed = 256;
        }

        boolean topToBottom = height < 0;
        height = Math.abs(height);

        byte[] imageData = new byte[dataOffset - (14 + headerSize + 4 * colorsUsed)];
        data.get(imageData);


        ByteBuffer out;
        int size = width * height;
        if (bpp == 24) {
            out = ByteBuffer.allocate(12 + size * 3).order(ByteOrder.LITTLE_ENDIAN);
            out.putInt(size * 3);
            out.putInt(width);
            out.putInt(height);

            int lineSize = (width * 3 + 3) / 4 * 4;

            int y, end, inc;
            if (topToBottom) {
                y = 0;
                end = height;
                inc = 1;
            } else {
                y = height - 1;
                end = -1;
                inc = -1;
            }

            int position = data.position();
            for (; y != end; y += inc) {
                data.position(position + lineSize * y);
                for (int x = 0; x < width; x++) {
                    byte b = data.get();
                    byte g = data.get();
                    byte r = data.get();
                    out.put(b);
                    out.put(g);
                    out.put(r);
                }
            }
        } else {
            out = ByteBuffer.allocate(12 + 256 * 3 + size).order(ByteOrder.LITTLE_ENDIAN);
            out.putInt(size);
            out.putInt(width);
            out.putInt(height);

            byte[] r = new byte[colorsUsed];
            byte[] g = new byte[colorsUsed];
            byte[] b = new byte[colorsUsed];
            for (int i = 0; i < colorsUsed; i++) {
                b[i] = data.get();
                g[i] = data.get();
                r[i] = data.get();
                data.get();
            }

            int lineSize = (width * 8 + 31) / 32 * 4;

            int y, end, inc;
            if (topToBottom) {
                y = 0;
                end = height;
                inc = 1;
            } else {
                y = height - 1;
                end = -1;
                inc = -1;
            }

            int position = data.position();

            for (; y != end; y += inc) {
                data.position(position + lineSize * y);
                for (int x = 0; x < width; x++) {
                    out.put(data.get());
                }
            }

            for (int i = 0; i < colorsUsed; i++) {
                out.put(r[i]).put(g[i]).put(b[i]);
            }
        }

        return out.flip();
    }

    public static ImageData toPng(byte[] data) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));

        int size = Integer.reverseBytes(input.readInt());
        int width = Integer.reverseBytes(input.readInt());
        int height = Integer.reverseBytes(input.readInt());
        boolean hasPalette = size == width * height;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageInfo header = new ImageInfo(width, height, 8, false, false, hasPalette);
        PngWriter pngWriter = new PngWriter(out, header);

        if (hasPalette) {
            PngChunkPLTE plteChunk = pngWriter.getMetadata().createPLTEChunk();
            plteChunk.setNentries(256);
            for (int i = 0; i < 256 * 3; i += 3) {
                int r = data[size + 12 + i] & 0xff;
                int g = data[size + 12 + i + 1] & 0xff;
                int b = data[size + 12 + i + 2] & 0xff;
                plteChunk.setEntry(i / 3, r, g, b);
            }

            for (int y = 0; y < height; y++) {
                ImageLineByte line = new ImageLineByte(header);
                for (int x = 0; x < width; x++) {
                    line.getScanline()[x] = data[12 + y * width + x];
                }
                pngWriter.writeRow(line);
            }
        } else {
            for (int y = 0; y < height; y++) {
                ImageLineInt line = new ImageLineInt(header);
                for (int x = 0; x < width; x++) {
                    int r = data[12 + y * width * 3 + x * 3 + 2] & 0xff;
                    int g = data[12 + y * width * 3 + x * 3 + 1] & 0xff;
                    int b = data[12 + y * width * 3 + x * 3] & 0xff;
                    ImageLineHelper.setPixelRGB8(line, x, r, g, b);
                }
                pngWriter.writeRow(line);
            }
        }

        pngWriter.end();

        return new ImageData(hasPalette, out.toByteArray());
    }

}
