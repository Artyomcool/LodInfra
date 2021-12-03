package com.github.artyomcool.lodinfra;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class LodFilePatch implements Closeable {

    private static final int HEADER_SIZE = getLodHeaderSize();
    private static final int SUB_FILE_HEADER_SIZE = getLodMetaHeaderSize();

    private final Path lodPath;
    private final LodFile file;
    private final Map<String, LodFile.SubFileMeta> originalSubFilesByName = new HashMap<>();
    private final Set<String> removedOriginalSubFiles = new HashSet<>();
    private final Map<String, Resource> patchesByName = new HashMap<>();

    private final Inflater inflater = new Inflater();
    private final Deflater deflater;

    public static LodFilePatch fromPath(Path path, int compressionLevel) throws IOException {
        return new LodFilePatch(path, LodFile.loadOrCreate(path), compressionLevel);
    }

    private LodFilePatch(Path lodPath, LodFile file, int compressionLevel) {
        this.lodPath = lodPath;
        this.file = file;
        this.deflater = new Deflater(compressionLevel);
        for (LodFile.SubFileMeta subFile : file.subFiles) {
            String name = sanitizeName(subFile.name);
            originalSubFilesByName.put(name, subFile);
        }
    }

    public void removeAllFromOriginal() {
        removedOriginalSubFiles.addAll(originalSubFilesByName.keySet());
    }

    public void removeFromOriginal(String name) {
        name = sanitizeName(name);
        if (originalSubFilesByName.containsKey(name)) {
            removedOriginalSubFiles.add(name);
        }
    }

    public void addPatch(Resource resource) {
        patchesByName.put(sanitizeName(resource.name), Resource.compress(resource, deflater));
    }

    private byte[] nameBytes(String name) {
        return Arrays.copyOf(name.getBytes(), 16);
    }

    public String calculateDiff() throws DataFormatException {
        StringBuilder diff = new StringBuilder();

        ByteBuffer uncompressedNewBuffer = ByteBuffer.allocate(
                patchesByName.values().stream()
                        .mapToInt(r -> r.uncompressedSize)
                        .max()
                        .orElse(1)
        );
        ByteBuffer uncompressedOldBuffer = ByteBuffer.allocate(
                originalSubFilesByName.values().stream()
                        .mapToInt(r -> r.uncompressedSize)
                        .max()
                        .orElse(1)
        );

        for (Map.Entry<String, Resource> entry : patchesByName.entrySet()) {
            String sanitizedName = entry.getKey();
            Resource resource = entry.getValue();

            LodFile.SubFileMeta old = originalSubFilesByName.get(sanitizedName);
            if (old == null) {
                diff.append("Added: ").append(resource.name).append("\n");
                continue;
            }

            if (resource.uncompressedSize != 0 && old.compressedSize != 0) {
                if (resource.data.equals(ByteBuffer.wrap(
                        file.originalData,
                        old.globalOffsetInFile,
                        old.compressedSize
                ))) {
                    continue;
                }
            }

            ByteBuffer uncompressedNew;
            ByteBuffer uncompressedOld;

            if (resource.uncompressedSize == 0) {
                uncompressedNew = resource.data;
            } else {
                uncompressedNewBuffer.clear();
                inflater.reset();
                inflater.setInput(resource.data.asReadOnlyBuffer());
                inflater.inflate(uncompressedNewBuffer);
                uncompressedNewBuffer.flip();

                uncompressedNew = uncompressedNewBuffer;
            }

            if (old.compressedSize == 0) {
                uncompressedOld = ByteBuffer.wrap(
                        file.originalData,
                        old.globalOffsetInFile,
                        old.uncompressedSize
                );
            } else {
                uncompressedOldBuffer.clear();
                inflater.reset();
                inflater.setInput(file.originalData, old.globalOffsetInFile, old.compressedSize);
                inflater.inflate(uncompressedOldBuffer);
                uncompressedOldBuffer.flip();

                uncompressedOld = uncompressedOldBuffer;
            }

            if (uncompressedNew.equals(uncompressedOld)) {
                continue;
            }

            diff.append("Changed: ").append(resource.name).append("\n");
            if (!sanitizedName.endsWith(".TXT")) {
                continue;
            }

            String newText = toString(uncompressedNew);
            String oldText = toString(uncompressedOld);

            String[] newTextLines = newText.split("\r\n", -1);
            String[] oldTextLines = oldText.split("\r\n", -1);

            for (int i = 0; i < Math.min(oldTextLines.length, newTextLines.length); i++) {
                String oldString = oldTextLines[i];
                String newString = newTextLines[i];
                if (oldString.equals(newString)) {
                    continue;
                }

                diff.append(String.format("    | Replace #%4d: ", i)).append(StringDiffer.diff(oldString, newString));
                diff.append("\n");
            }

            for (int i = Math.min(oldTextLines.length, newTextLines.length); i < newTextLines.length; i++) {
                diff.append(String.format("    | Add #%4d: ", i));
                newTextLines[i].chars().forEach(c -> StringDiffer.append(diff, (char) c));
                diff.append("\n");
            }
            for (int i = Math.min(oldTextLines.length, newTextLines.length); i < oldTextLines.length; i++) {
                diff.append(String.format("    | Removed #%4d: ", i));
                oldTextLines[i].chars().forEach(c -> StringDiffer.append(diff, (char) c));
                diff.append("\n");
            }
        }
        return diff.toString();
    }

    private static String toString(ByteBuffer uncompressedNew) {
        byte[] arr = new byte[uncompressedNew.remaining()];
        uncompressedNew.asReadOnlyBuffer().get(arr);
        return new String(arr, Utils.cp1251);
    }

    public ByteBuffer serialize() {
        List<Resource> resources = new ArrayList<>(patchesByName.values());
        originalSubFilesByName.forEach((sanitizedName, meta) -> {
            if (removedOriginalSubFiles.contains(sanitizedName) || patchesByName.containsKey(sanitizedName)) {
                return;
            }
            resources.add(Resource.fromLod(lodPath, file, meta));
        });
        resources.sort(Comparator.comparing(r -> r.name.toLowerCase()));

        int headersSize = HEADER_SIZE + SUB_FILE_HEADER_SIZE * resources.size();
        int contentSize = resources.stream().mapToInt(r -> r.data.remaining()).sum();

        byte[] result = new byte[headersSize + contentSize];

        int subFilesCount = getSubFilesCount();

        ByteBuffer byteBuffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt(file.magic);
        byteBuffer.putInt(file.fileUseFlag);
        byteBuffer.putInt(subFilesCount);
        byteBuffer.put(file.junk);

        int offset = headersSize;

        for (Resource resource : resources) {

            int compressedSize = resource.uncompressedSize == 0
                    ? 0
                    : resource.data.remaining();

            int uncompressedSize = resource.uncompressedSize == 0
                    ? resource.data.remaining()
                    : resource.uncompressedSize;

            byteBuffer.put(nameBytes(resource.name));
            byteBuffer.putInt(offset);
            byteBuffer.putInt(uncompressedSize);
            byteBuffer.putInt(resource.type);
            byteBuffer.putInt(compressedSize);

            offset += resource.data.remaining();
        }

        for (Resource resource : resources) {
            byteBuffer.put(resource.data.asReadOnlyBuffer());
        }

        return byteBuffer.flip();
    }

    private int getSubFilesCount() {
        return subFilesToPreserve().size() + patchesByName.values().size();
    }

    private List<LodFile.SubFileMeta> subFilesToPreserve() {
        List<LodFile.SubFileMeta> result = new ArrayList<>(file.subFiles);
        result.removeIf(f -> {
            String name = sanitizeName(f.name);
            return removedOriginalSubFiles.contains(name) || patchesByName.containsKey(name);
        });
        return result;
    }

    private static int getLodHeaderSize() {
        LodFile file = LodFile.createEmpty();

        int size = 0;

        size += size(file.magic);
        size += size(file.fileUseFlag);
        size += size(file.subFilesCount);
        size += size(file.junk);

        return size;
    }

    private static int getLodMetaHeaderSize() {
        LodFile.SubFileMeta subFile = new LodFile.SubFileMeta();

        int size = 0;

        size += size(subFile.name);
        size += size(subFile.globalOffsetInFile);
        size += size(subFile.uncompressedSize);
        size += size(subFile.fileType);
        size += size(subFile.compressedSize);

        return size;
    }

    private static int size(@SuppressWarnings("unused") int d) {
        return Integer.BYTES;
    }

    private static int size(byte[] d) {
        return d.length;
    }

    private static String sanitizeName(byte[] name) {
        return sanitizeName(nameToString(name));
    }

    private static String nameToString(byte[] name) {
        return new String(name).trim();
    }

    private static String sanitizeName(String name) {
        return name.toUpperCase();
    }

    @Override
    public void close() {
        inflater.end();
        deflater.end();
    }
}
