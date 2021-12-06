package com.github.artyomcool.lodinfra;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
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

    public final Path dir;
    public final String pathPattern;

    public boolean dry = false;
    public boolean logDetailedDiff = false;
    public Instant ignoreBeforeTimestamp = Instant.MIN;
    public int compressionLevel = 0;
    public Set<String> allowedLangs = new HashSet<>();
    public Set<String> dontWarnAboutNames = new HashSet<>();

    public DirectoryResourceCollector(Path dir, String pathPattern) {
        this.dir = dir;
        this.pathPattern = pathPattern;
    }

    public void collectResources() throws IOException, DataFormatException {

        Map<String, List<Path>> xlsFilesByLodName = new HashMap<>();
        Map<String, List<Path>> resourcesByLangLodName = new HashMap<>();

        List<Path> files = Files.list(dir).collect(Collectors.toList());
        for (Path path : files) {
            String name = path.getFileName().toString();
            if (Files.isDirectory(path)) {
                if (!name.contains("@")) {
                    continue;
                }

                String lang = name.substring(0, name.indexOf("@"));
                if (!allowedLangs.isEmpty() && !allowedLangs.contains(lang.toLowerCase())) {
                    continue;
                }

                System.out.println("Collecting files from " + name);

                ArrayList<Path> langAndLodCollection = new ArrayList<>();
                resourcesByLangLodName.put(name, langAndLodCollection);

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
                        langAndLodCollection.add(path);
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
                    System.out.println("Xls " + xlsPath + " is modified at " + lastModifiedTime + ", ignoring (last timestamp is " + ignoreBeforeTimestamp + ")");
                    continue;
                }

                System.out.println("Collecting texts from " + xlsPath.getFileName());

                try (
                        InputStream stream = Files.newInputStream(xlsPath);
                        XSSFWorkbook sheets = new XSSFWorkbook(stream)
                ) {
                    List<Resource> resources = XlsTextExtractor.extractResources(xlsPath, sheets);
                    for (Resource resource : resources) {
                        if (!allowedLangs.isEmpty() && !allowedLangs.contains(resource.lang)) {
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

        Map<Path, String> logsByLod = writeLods(lodToResource);
        System.out.println("Lods written");

        if (logDetailedDiff) {
            writeLog(logsByLod);
        }
    }

    private Map<Path, String> writeLods(Map<Path, LodResources> lodToResource) throws IOException, DataFormatException {
        Map<Path, String> logsByLod = new LinkedHashMap<>();

        try (ResourcePreprocessor resourcePreprocessor = new ResourcePreprocessor(compressionLevel)) {

            for (Map.Entry<Path, LodResources> entry : lodToResource.entrySet()) {
                Path lodPath = entry.getKey();
                if (entry.getValue().resourcesByName.isEmpty()) {
                    continue;
                }

                System.out.println("Packing " + lodPath);
                System.out.println("Preprocess resources");

                for (Map.Entry<String, Resource> resource : entry.getValue().resourcesByName.entrySet()) {
                    String lowName = resource.getValue().name.toLowerCase();
                    if (lowName.length() > 12) {
                        if (lowName.length() > 15) {
                            throw new RuntimeException("Resource lowName '" + lowName + "' is too long");
                        } else {
                            if (!dontWarnAboutNames.contains(lowName)) {
                                System.out.println("NOTE: Resource lowName '" + lowName + "' is longer then 12 chars, game treats it as '" + lowName.substring(0, 12) + "'");
                            }
                        }
                    }

                    resource.setValue(resourcePreprocessor.compressed(resource.getValue()));
                }

                System.out.println("Write " + lodPath);
                LodFilePatch lodFilePatch = LodFilePatch.fromPath(lodPath, resourcePreprocessor);
                if (ignoreBeforeTimestamp.equals(Instant.MIN)) {
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
                        channel.force(true);
                    }
                }
            }
        }
        return logsByLod;
    }

    private void writeLog(Map<Path, String> logsByLod) throws IOException {
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


    private static class LodResources {
        final Map<String, Resource> resourcesByName = new LinkedHashMap<>();

        public void addResource(Resource resource) throws IOException {
            Resource old = resourcesByName.put(resource.sanitizedName, resource);
            if (old != null) {
                throw new IOException("File duplicated: " + old.virtualPath + " and " + resource.virtualPath);
            }
        }
    }

}
