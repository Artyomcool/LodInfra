package com.github.artyomcool.lodinfra;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

public class DirectoryResourceCollector {

    private static class LodResources {
        final Map<String, Resource> resourcesByName = new LinkedHashMap<>();

        public void addResource(Resource resource) throws IOException {
            Resource old = resourcesByName.put(resource.name, resource);
            if (old != null) {
                throw new IOException("File duplicated: " + old.virtualPath + " and " + resource.virtualPath);
            }
        }
    }

    public static void collectResources(Path dir, Path outputRoot, Path afterLangPath, boolean dry) throws IOException, InvalidFormatException, DataFormatException {

        Map<String, List<Path>> xlsFilesByLodName = new HashMap<>();
        Map<String, List<Path>> resourcesByLangLodName = new HashMap<>();

        List<Path> files = Files.list(dir).collect(Collectors.toList());
        for (Path path : files) {
            String name = path.getFileName().toString();
            if (Files.isDirectory(path)) {
                if (!name.contains("@")) {
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

        for (Map.Entry<String, List<Path>> entry : xlsFilesByLodName.entrySet()) {
            String lodName = entry.getKey() + ".lod";
            for (Path xlsPath : entry.getValue()) {
                try (XSSFWorkbook sheets = new XSSFWorkbook(xlsPath.toFile())) {
                    List<Resource> resources = extractResources(xlsPath, sheets);
                    for (Resource resource : resources) {
                        Path lodPath = outputRoot
                                .resolve(resource.lang)
                                .resolve(afterLangPath)
                                .resolve(lodName);

                        lodToResource.computeIfAbsent(lodPath, k -> new LodResources()).addResource(resource);
                    }
                }
            }
        }

        for (Map.Entry<String, List<Path>> entry : resourcesByLangLodName.entrySet()) {
            String[] parts = entry.getKey().split("@", 2);
            String lang = parts[0];
            String lodName = parts[1] + ".lod";
            Path lodPath = outputRoot
                    .resolve(lang)
                    .resolve(afterLangPath)
                    .resolve(lodName);

            LodResources resources = lodToResource.computeIfAbsent(lodPath, k -> new LodResources());
            for (Path path : entry.getValue()) {
                Resource resource = Resource.fromPath(lang, path);
                resources.addResource(resource);
            }
        }

        Map<Path, String> logsByLod = writeLods(dry, lodToResource);

        writeLog(dir, logsByLod);
    }

    private static List<Resource> extractResources(Path path, XSSFWorkbook sheets) {
        List<Resource> resources = new ArrayList<>();
        sheets.sheetIterator().forEachRemaining(sheet -> {
            Row firstRow = sheet.getRow(0);
            if (firstRow == null
                    || firstRow.getCell(0) == null
                    || firstRow.getCell(0).toString().isBlank()) {
                return;
            }

            String name = sheet.getSheetName();
            int lastRowNum = getLastRowNum(sheet);

            Set<Integer> filterBlank = new HashSet<>();

            Map<String, StringBuilder> allLangs = new HashMap<>();
            List<Collection<StringBuilder>> textsByCellIndex = new ArrayList<>();

            for (int cellIndex = 0; cellIndex < firstRow.getLastCellNum(); cellIndex++) {
                Cell cell = firstRow.getCell(cellIndex);
                if (cell == null || cell.getCellType() != CellType.STRING || cell.getStringCellValue().isBlank()) {
                    break;
                }

                String lang = cell.toString();
                String[] langParts = lang.split(":");
                lang = langParts[0];
                if (langParts.length > 1 && langParts[1].equals("not_blank")) {
                    filterBlank.add(cellIndex);
                }

                if (lang.equals("common")) {
                    textsByCellIndex.add(allLangs.values());
                } else {
                    StringBuilder text = allLangs.computeIfAbsent(lang, k -> new StringBuilder());
                    textsByCellIndex.add(Collections.singletonList(text));
                }
            }


            for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                Set<StringBuilder> firstCellVisited = Collections.newSetFromMap(new IdentityHashMap<>());

                for (int cellIndex = 0; cellIndex < textsByCellIndex.size(); cellIndex++) {
                    Cell cell = row == null ? null : row.getCell(cellIndex);
                    if (filterBlank.contains(cellIndex)) {
                        if (cell == null || cell.toString().isBlank()) {
                            continue;
                        }
                    }
                    for (StringBuilder text : textsByCellIndex.get(cellIndex)) {
                        if (!firstCellVisited.add(text)) {
                            text.append('\t');
                        }
                        if (cell == null) {
                            continue;
                        }

                        String cellText = getCellText(cell);

                        text.append(cellText);
                    }
                }

                for (StringBuilder text : allLangs.values()) {
                    text.append("\r\n");
                }
            }

            allLangs.forEach((k, v) -> resources.add(
                    Resource.fromString(
                            path,
                            k,
                            name,
                            v.toString()
                    )
            ));
        });

        return resources;
    }

    public static String getCellText(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return ((XSSFCell) cell).getRawValue();
        }
        String text = cell.toString()
                .replaceAll("\"", "\"\"")
                .replaceAll("\r\n", "\n");

        return text.contains("\n")
                ? "\"" + text + "\""
                : text;
    }

    private static int getLastRowNum(Sheet sheet) {
        for (int lastRowNum = sheet.getLastRowNum(); lastRowNum > 0; lastRowNum--) {
            Row row = sheet.getRow(lastRowNum);
            if (row == null) {
                continue;
            }
            Cell cell = row.getCell(0);
            if (cell != null && cell.getCellType() == CellType.STRING && cell.getStringCellValue().startsWith("###")) {
                return lastRowNum - 1;
            }
            Iterator<Cell> cellIterator = row.cellIterator();
            while (cellIterator.hasNext()) {
                Cell next = cellIterator.next();
                if (next != null) {
                    if (!next.toString().isBlank()) {
                        return lastRowNum;
                    }
                }
            }
        }
        return 0;
    }

    private static Map<Path, String> writeLods(boolean dry, Map<Path, LodResources> lodToResource) throws IOException, DataFormatException {
        Map<Path, String> logsByLod = new LinkedHashMap<>();

        for (Map.Entry<Path, LodResources> entry : lodToResource.entrySet()) {
            Path lodPath = entry.getKey();
            try (LodFilePatch lodFilePatch = LodFilePatch.fromPath(lodPath)) {
                lodFilePatch.removeAllFromOriginal();

                for (Resource resource : entry.getValue().resourcesByName.values()) {
                    lodFilePatch.addPatch(resource);
                }

                String logs = lodFilePatch.calculateDiff();
                logsByLod.put(lodPath, logs);

                if (!dry) {
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
            log.lines().forEach(l -> logRecord.append("\t\t").append(l).append("\n"));
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
