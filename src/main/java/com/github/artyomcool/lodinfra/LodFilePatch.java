package com.github.artyomcool.lodinfra;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.DataFormatException;

public class LodFilePatch {

    private static final int HEADER_SIZE = getLodHeaderSize();
    private static final int SUB_FILE_HEADER_SIZE = getLodMetaHeaderSize();

    private final LodFile file;
    private final Map<String, Resource> originalResourcesByName = new LinkedHashMap<>();
    private final Set<String> removedByName = new LinkedHashSet<>();
    private final Map<String, Resource> patchesByName = new LinkedHashMap<>();

    private final ResourcePreprocessor preprocessor;

    public static LodFilePatch fromPath(Path path, ResourcePreprocessor preprocessor) throws IOException {
        return new LodFilePatch(path, LodFile.loadOrCreate(path), preprocessor);
    }

    private LodFilePatch(Path lodPath, LodFile file, ResourcePreprocessor preprocessor) {
        this.file = file;
        this.preprocessor = preprocessor;
        for (LodFile.SubFileMeta subFile : file.subFiles) {
            Resource resource = Resource.fromLod(lodPath, file, subFile);
            originalResourcesByName.put(resource.sanitizedName, resource);
        }
    }

    public void removeAllFromOriginal() {
        removedByName.addAll(originalResourcesByName.keySet());
    }

    public void removeFromOriginal(String name) {
        name = Resource.sanitizeName(name);
        if (originalResourcesByName.containsKey(name)) {
            removedByName.add(name);
        }
    }

    public void addPatch(Resource resource) {
        patchesByName.put(resource.sanitizedName, resource);
    }

    private byte[] nameBytes(String name) {
        boolean invalidChar = name.chars().anyMatch(c -> !(
                (c >= 'A' && c <= 'Z')
                        || (c >= 'a' && c <= 'z')
                        || c == '.'
                        || c == '_'
                        || (c >= '0' && c <= '9')
                )
        );
        if (invalidChar) {
            throw new RuntimeException("Resource name '" + name + "' has invalid name");
        }

        byte[] bytes = name.getBytes(StandardCharsets.US_ASCII);
        return Arrays.copyOf(bytes, 16);
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
                originalResourcesByName.values().stream()
                        .mapToInt(r -> r.uncompressedSize)
                        .max()
                        .orElse(1)
        );

        for (Map.Entry<String, Resource> entry : patchesByName.entrySet()) {
            String sanitizedName = entry.getKey();
            Resource resource = entry.getValue();

            Resource old = originalResourcesByName.get(sanitizedName);
            if (old == null) {
                diff.append("Added: ").append(resource.name).append("\n");
                continue;
            }

            if (resource.uncompressedSize != 0 && old.uncompressedSize != 0) {
                if (resource.data.equals(old.data)) {
                    continue;
                }
            }

            ByteBuffer uncompressedNew;
            ByteBuffer uncompressedOld;

            if (resource.uncompressedSize == 0) {
                uncompressedNew = resource.data;
            } else {
                uncompressedNew = preprocessor.uncompressed(resource, uncompressedNewBuffer);
            }

            if (old.uncompressedSize == 0) {
                uncompressedOld = old.data;
            } else {
                uncompressedOld = preprocessor.uncompressed(old, uncompressedOldBuffer);
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

        for (String removed : removedByName) {
            if (patchesByName.containsKey(removed)) {
                continue;
            }
            diff.append("Removed: ").append(removed).append("\n");
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
        originalResourcesByName.forEach((sanitizedName, resource) -> {
            if (removedByName.contains(sanitizedName) || patchesByName.containsKey(sanitizedName)) {
                return;
            }
            resources.add(resource);
        });
        resources.sort(Comparator.comparing(r -> r.sanitizedName));

        int headersSize = HEADER_SIZE + SUB_FILE_HEADER_SIZE * resources.size();
        int contentSize = resources.stream().mapToInt(r -> r.data.remaining()).sum();

        byte[] result = new byte[headersSize + contentSize];

        int subFilesCount = resources.size();

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

    private static String nameToString(byte[] name) {
        return new String(name).trim();
    }

}
