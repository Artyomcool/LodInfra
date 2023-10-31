package com.github.artyomcool.lodinfra.h3common;

import java.util.*;

public class DefCompressor {

    private final Map<Long, Integer> frameIndexes = new HashMap<>();
    private final Map<FrameData, Integer> deduplication = new HashMap<>();
    private final List<FrameData> frames = new ArrayList<>();

    private final Map<Integer, Integer> paletteDistribution = new HashMap<>();
    private final Map<Integer, Integer> palette = new LinkedHashMap<>();
    private final Map<Integer, Integer> specColors = new HashMap<>();

    private int maxGroup = 0;

    public void add(int frame, int group, int[][] image, int compression) {
        FrameData data = new FrameData(image, compression);
        Integer index = deduplication.putIfAbsent(data, frames.size());
        if (index == null) {
            index = frames.size();
            frames.add(data);

            for (int[] row : data.data) {
                for (int color : row) {
                    palette.putIfAbsent(color, palette.size());
                    paletteDistribution.compute(color, (k, v) -> (v == null ? 1 : v + 1));
                }
            }
        }
        frameIndexes.put(frame | (long) group << 32, index);

        maxGroup = Math.max(group, maxGroup);
    }
/*
    public Def compress(int defType) {
        if (this.palette.size() > 256) {
            throw new IllegalStateException("Palette is huge");
        }

        int fullWidth = 0;
        int fullHeight = 0;
        for (FrameData frame : frames) {
            int height = frame.data.length;
            if (height > 0) {
                fullHeight = Math.max(fullHeight, height);
                fullWidth = Math.max(fullWidth, frame.data[0].length);
            }
        }

        int groupCount = maxGroup + 1;

        int[] palette = new int[256];
        this.palette.forEach((color, index) -> {
            palette[index] = color;
        });

        int size = 0;
        size += sizeOf(defType);
        size += sizeOf(fullWidth);
        size += sizeOf(fullHeight);
        size += sizeOf(groupCount);
        size += sizeOf(palette);

        int[][] groupToFrames = new int[groupCount][];
        for (int group = 0; group < groupCount; group++) {
            int[] frames = frames(group);
            groupToFrames[group] = frames;

            size += sizeOf(group);
            size += sizeOf(frames.length);
            size += sizeOf(0);
            size += sizeOf(0);

            size += 13 * frames.length;

            size += sizeOf(frames);
        }

        int[] globalOffsets = new int[frames.size()];

        for (int i = 0; i < frames.size(); i++) {
            globalOffsets[i] = size;
            int frameSize = frames.get(i).size();
            size += frameSize;
        }

        ByteBuffer buffer = ByteBuffer.allocate(size);

    }
*/
    private int[] frames(int group) {
        TreeMap<Integer, Integer> frames = new TreeMap<>();
        for (Map.Entry<Long, Integer> entry : frameIndexes.entrySet()) {
            long frame = entry.getKey();
            if (frame >>> 32 == group) {
                frames.put((int)frame, entry.getValue());
            }
        }
        int[] result = new int[frames.size()];
        int i = 0;
        for (int frameIndex : frames.keySet()) {
            result[i++] = frameIndex;
        }
        return result;
    }

    public void preparePalette() {
        Map<Integer, Integer> newPalette = new LinkedHashMap<>();
        Map<Integer, Integer> newSpecColors = new HashMap<>();
        for (int specColor : Def.SPEC_COLORS) {
            if (palette.remove(specColor) != null) {
                newPalette.put(specColor, newPalette.size());
                newSpecColors.put(specColor, newSpecColors.size());
            }
        }
        for (Integer key : palette.keySet()) {
            newPalette.put(key, newPalette.size());
        }
        palette.clear();
        palette.putAll(newPalette);

        specColors.clear();
        specColors.putAll(newSpecColors);

        if (newPalette.size() > 256) {
            throw new IllegalStateException("Palette is huge");
        }
    }

    private static int sizeOf(@SuppressWarnings("unused") short data) {
        return Short.BYTES;
    }

    private static int sizeOf(@SuppressWarnings("unused") int data) {
        return Integer.BYTES;
    }

    private static int sizeOf(int[] data) {
        return data.length * Integer.BYTES;
    }

    public static class Row {
        private final int[] data;
        private final int hash;

        public Row(int[] data) {
            this.data = data;
            this.hash = Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Row row = (Row) o;

            if (hash != row.hash) return false;
            return Arrays.equals(data, row.data);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(data);
            result = 31 * result + hash;
            return result;
        }
/*
        public void compress(ByteBuffer buffer, int compression, Map<Integer, Integer> colors, Map<Integer, Integer> specColors) {
            switch (compression) {
                case 1 -> {
                    for (int w = 0; w < data.length; ) {
                        int color = data[w];
                        Integer spec = specColors.get(color);
                        if (spec == null) {
                            buffer.putInt(0xff);
                            buffer.

                            for (int i = 0; w < data.length && i < 256; i++, w++) {

                            }
                        } else {
                            buffer.putInt(color);


                        }
                        size += sizeOf(color);
                        int count = 1;
                        while (true) {
                            int p = data[w++];
                            if (w >= data.length || p != data[w]) {
                                break;
                            }
                            count++;
                        }
                        size += sizeOf(count);
                    }
                    yield size;
                }
        }

        public int size(int compression, Set<Integer> specColors) {
            return switch (compression) {
                case 1 -> {
                    int size = 0;
                    for (int w = 0; w < data.length; ) {
                        int color = data[w];
                        specColors
                        size += sizeOf(color);
                        int count = 1;
                        while (true) {
                            int p = data[w++];
                            if (w >= data.length || p != data[w]) {
                                break;
                            }
                            count++;
                        }
                        size += sizeOf(count);
                    }
                    yield size;
                }
                case 2 -> {
                    int size = 0;
                    for (int w = 0; w < data.length; ) {
                        int c
                    }

                    for (int w = 0; w < width; ) {
                        int b = buffer.get() & 0xff;
                        int index = b >> 5;
                        int count = (b & 0x1f) + 1;
                        for (int j = 0; j < count; j++) {
                            image[yy][xx] = index == 0x7 ? palette[buffer.get() & 0xff] : SPEC_COLORS[index];
                            xx++;
                            if (xx >= x + width) {
                                yy++;
                                xx = x;
                            }
                        }
                        w += count;
                    }
                }
                default -> throw new IllegalStateException("Unexpected value: " + compression);
            };
        }*/
    }

    public static class FrameData {
        private final int[][] data;
        private final int compression;

        private int hash;
        private int l,r,t,b;

        public FrameData(int[][] data, int compression) {
            this.data = data;
            this.compression = compression;
            recalc();
        }

        public void recalc() {
            l = r = t = b = 0;
            if (data.length == 0 || data[0].length == 0) {
                hash = 0;
                return;
            }

            for (int y = 0; y < data.length; y++) {
                int x = firstVisibleX(data[y]);
                if (x != -1) {
                    l = r = x;
                    t = b = y;
                    break;
                }
            }

            for (int y = data.length - 1; y >= 0; y--) {
                int x = firstVisibleX(data[y]);
                if (x != -1) {
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    b = y;
                    break;
                }
            }

            for (int x = 0; x <= l; x++) {
                int y = firstVisibleY(data, x, t, b);
                if (y != -1) {
                    l = x;
                    break;
                }
            }

            for (int x = data[0].length - 1; x > r; x--) {
                int y = firstVisibleY(data, x, t, b);
                if (y != -1) {
                    r = x;
                    break;
                }
            }

            int hash = ((l * 31 + r) * 31 + t) * 31 + b;
            for (int y = t; y <= b; y++) {
                int[] row = data[y];
                for (int x = l; x <= r; x++) {
                    hash = hash * 31 + row[x];
                }
            }
            this.hash = hash;
        }
/*
        public int size() {
            int size = 0;
            int w = r - l + 1;
            int h = b - t + 1;

            size += sizeOf(0);
            size += sizeOf(compression);
            size += sizeOf(data.length == 0 ? 0 : data[0].length);
            size += sizeOf(data.length);

            size += sizeOf(w);
            size += sizeOf(h);
            size += sizeOf(l);
            size += sizeOf(t);

            switch (compression) {
                case 0 -> size += w * h;
                case 1 -> {
                    Map<Row, Integer> rowToIndex = new HashMap<>();
                    for (int[] color : data) {
                        rowToIndex.putIfAbsent(new Row(color), rowToIndex.size());
                    }
                    size += rowToIndex.size() * sizeOf(0);
                    for (Row row : rowToIndex.keySet()) {
                        size += row.size(1);
                    }
                }
                case 2 -> {
                    Map<Row, Integer> rowToIndex = new HashMap<>();
                    for (int[] color : data) {
                        rowToIndex.putIfAbsent(new Row(color), rowToIndex.size());
                    }
                    size += rowToIndex.size() * sizeOf((short) 0);
                    for (Row row : rowToIndex.keySet()) {
                        size += row.size(2);
                    }
                }
                case 3 -> {
                    int[] offsets = new int[width * height / 32];
                    for (int i = 0; i < offsets.length; i++) {
                        offsets[i] = (buffer.getShort() & 0xffff) + start;
                    }
                    for (int i : offsets) {
                        buffer.position(i);

                        int left = 32;
                        while (left > 0) {
                            int b = buffer.get() & 0xff;
                            int index = b >> 5;
                            int count = (b & 0x1f) + 1;

                            for (int j = 0; j < count; j++) {
                                image[yy][xx] = index == 0x7 ? palette[buffer.get() & 0xff] : SPEC_COLORS[index];
                                xx++;
                                if (xx >= x + width) {
                                    yy++;
                                    xx = x;
                                }
                            }

                            left -= count;
                        }
                    }
                }
            }


            return ;
        }

        public void compress(ByteBuffer buffer) {
            buffer
        }
*/
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FrameData frameData = (FrameData) o;

            if (hash != frameData.hash) return false;
            if (l != frameData.l) return false;
            if (r != frameData.r) return false;
            if (t != frameData.t) return false;
            if (b != frameData.b) return false;

            for (int y = t; y <= b; y++) {
                int[] row = data[y];
                int[] oRow = data[y];
                if (!Arrays.equals(row, l, r + 1, oRow, l, r + 1)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        private static int firstVisibleX(int[] row) {
            int alpha = Def.SPEC_COLORS[0];
            for (int x = 0; x < row.length; x++) {
                if (row[x] != alpha) {
                    return x;
                }
            }
            return -1;
        }

        private static int firstVisibleY(int[][] data, int x, int t, int b) {
            int alpha = Def.SPEC_COLORS[0];
            for (int y = t; y <= b; y++) {
                if (data[y][x] != alpha) {
                    return y;
                }
            }
            return -1;
        }
    }
}
