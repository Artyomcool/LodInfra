package com.github.artyomcool.lodinfra;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

public class DirectoryResourceCollector {

    public static final String INCREMENTAL_STATE_FILE = "lastTs";

    private static class LodResources {
        final Map<String, Resource> resourcesByName = new LinkedHashMap<>();

        public void addResource(Resource resource) throws IOException {
            Resource old = resourcesByName.put(resource.name, resource);
            if (old != null) {
                throw new IOException("File duplicated: " + old.virtualPath + " and " + resource.virtualPath);
            }
        }
    }

    // TODO move config into constructor
    public static void collectResources(
            Path dir,
            String pathPattern,
            boolean dry,
            boolean logDetailedDiff,
            boolean checkTimestamps,
            int compressionLevel,
            String[] ignoreLangs
    ) throws IOException, DataFormatException {
        Instant ignoreBeforeTimestamp = Instant.MIN;
        Instant now = Instant.now();
        if (checkTimestamps) {
            try {
                String text = Files.readString(dir.resolve(INCREMENTAL_STATE_FILE));
                Properties properties = new Properties();
                properties.load(new StringReader(text));
                String prevIgnoreLangs = properties.getProperty("ignoreLangs");
                if (prevIgnoreLangs != null && prevIgnoreLangs.equals(Arrays.toString(ignoreLangs))) {
                    ignoreBeforeTimestamp = Instant.parse(properties.getProperty("ts"));
                } else {
                    System.out.println("No incremental state: language changed");
                }
            } catch (Exception ignored) {
                System.out.println("No valid incremental state");
            }
        }

        Map<String, List<Path>> xlsFilesByLodName = new HashMap<>();
        Map<String, List<Path>> resourcesByLangLodName = new HashMap<>();
        Set<String> ignoreLangsSet = new HashSet<>();
        for (String ignoreLang : ignoreLangs) {
            ignoreLangsSet.add(ignoreLang.toLowerCase());
        }

        List<Path> files = Files.list(dir).collect(Collectors.toList());
        for (Path path : files) {
            String name = path.getFileName().toString();
            if (Files.isDirectory(path)) {
                if (!name.contains("@")) {
                    continue;
                }

                String lang = name.substring(0, name.indexOf("@"));
                if (ignoreLangsSet.contains(lang.toLowerCase())) {
                    continue;
                }

                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) {
                        String name = path.getFileName().toString();
                        return name.startsWith(".") || name.startsWith("_")
                                ? FileVisitResult.SKIP_SUBTREE
                                : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
                        resourcesByLangLodName.computeIfAbsent(name, k -> new ArrayList<>()).add(path);
                        return FileVisitResult.CONTINUE;
                    }
                });

                continue;
            }

            if (!name.toLowerCase().endsWith(".xls") && !name.toLowerCase().endsWith(".xlsx")) {
                continue;
            }

            String[] subnames = name.split("\\$");
            String lodName = subnames[subnames.length - 1].split("\\.")[0];
            xlsFilesByLodName.computeIfAbsent(lodName, k -> new ArrayList<>()).add(path);
        }

        Map<Path, LodResources> lodToResource = new LinkedHashMap<>();

        int ignored = 0;
        int changed = 0;

        for (Map.Entry<String, List<Path>> entry : xlsFilesByLodName.entrySet()) {
            String lodName = entry.getKey();
            for (Path xlsPath : entry.getValue()) {

                FileTime lastModifiedTime = Files.getLastModifiedTime(xlsPath);
                if (lastModifiedTime.toInstant().isBefore(ignoreBeforeTimestamp)) {
                    System.out.println("Xls " + xlsPath + " is modified at " + lastModifiedTime + ", ignoring (now is " +now + ")");
                    continue;
                }

                try (
                        InputStream stream = Files.newInputStream(xlsPath);
                        XSSFWorkbook sheets = new XSSFWorkbook(stream)
                ) {
                    List<Resource> resources = XlsTextExtractor.extractResources(xlsPath, sheets);
                    for (Resource resource : resources) {
                        if (ignoreLangsSet.contains(resource.lang)) {
                            continue;
                        }
                        Path lodPath = Utils.resolveTemplate(pathPattern, resource.lang, lodName);
                        lodToResource.computeIfAbsent(lodPath, k -> new LodResources()).addResource(resource);
                        changed++;
                    }
                }
            }
        }

        for (Map.Entry<String, List<Path>> entry : resourcesByLangLodName.entrySet()) {
            String[] parts = entry.getKey().split("@", 2);
            String lang = parts[0];
            String lodName = parts[1];

            Path lodPath = Utils.resolveTemplate(pathPattern, lang, lodName);

            LodResources resources = lodToResource.computeIfAbsent(lodPath, k -> new LodResources());
            for (Path path : entry.getValue()) {
                FileTime lastModifiedTime = Files.getLastModifiedTime(path);
                if (lastModifiedTime.toInstant().isBefore(ignoreBeforeTimestamp)) {
                    ignored++;
                    continue;
                }

                Resource resource = Resource.fromPath(lang, path);
                resources.addResource(resource);
                changed++;
            }
        }

        if (ignored > 0) {
            System.out.println("Ignored " + ignored + " files, changed " + changed);
        }

        Map<Path, String> logsByLod = writeLods(lodToResource, dry, logDetailedDiff, checkTimestamps, compressionLevel);
        System.out.println("Lods written");

        if (logDetailedDiff) {
            writeLog(dir, logsByLod);
        }

        if (checkTimestamps) {
            Properties properties = new Properties();
            properties.put("ignoreLangs", Arrays.toString(ignoreLangs));
            properties.put("ts", now.toString());
            StringWriter writer = new StringWriter();
            properties.store(writer, "State of incremental work");
            Files.writeString(dir.resolve(INCREMENTAL_STATE_FILE), writer.toString());
        }
    }

    private static Map<Path, String> writeLods(Map<Path, LodResources> lodToResource, boolean dry, boolean logDetailedDiff, boolean preserveData, int compressionLevel) throws IOException, DataFormatException {
        Map<Path, String> logsByLod = new LinkedHashMap<>();

        for (Map.Entry<Path, LodResources> entry : lodToResource.entrySet()) {
            Path lodPath = entry.getKey();
            if (entry.getValue().resourcesByName.isEmpty()) {
                continue;
            }

            System.out.println("Packing " + lodPath);
            try (LodFilePatch lodFilePatch = LodFilePatch.fromPath(lodPath, compressionLevel)) {
                if (!preserveData) {
                    lodFilePatch.removeAllFromOriginal();
                }

                for (Resource resource : entry.getValue().resourcesByName.values()) {
                    lodFilePatch.addPatch(resource);
                }

                if (logDetailedDiff) {
                    String logs = lodFilePatch.calculateDiff();
                    logsByLod.put(lodPath, logs);
                }

                if (!dry) {
                    Files.createDirectories(lodPath.getParent());
                    ByteBuffer newLod = lodFilePatch.serialize();
                    try (FileChannel channel = FileChannel.open(
                            lodPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    )) {
                        channel.write(newLod);
                    }
                }
            }
        }
        return logsByLod;
    }

    private static void writeLog(Path dir, Map<Path, String> logsByLod) throws IOException {
        LocalDate now = LocalDate.now();
        Path logPath = dir.resolve("logs").resolve(String.format("%4d-%2d", now.getYear(), now.getMonth().getValue()));

        StringBuilder logRecord = new StringBuilder().append(now).append(":\n");
        logsByLod.forEach((lod, log) -> {
            if (log.isBlank()) {
                return;
            }
            logRecord.append("\t");
            logRecord.append(lod).append(":\n");

            System.out.println("Changes in " + lod + ":");
            log.lines().forEach(new Consumer<>() {
                int lineNumberToShow = 15;
                @Override
                public void accept(String l) {
                    logRecord.append("\t\t").append(l).append("\n");
                    if (lineNumberToShow == 0) {
                        System.out.println("... more");
                    } else if (lineNumberToShow > 0) {
                        System.out.println("    " + l);
                    }
                    lineNumberToShow--;
                }
            });
        });
        logRecord.append("\n");

        Files.createDirectories(logPath.getParent());
        Files.write(
                logPath,
                logRecord.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
        );
    }

}
