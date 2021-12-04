package com.github.artyomcool.lodinfra;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.nio.file.Path;
import java.util.*;

public class XlsTextExtractor {

    public static List<Resource> extractResources(Path path, XSSFWorkbook sheets) {
        List<Resource> resources = new ArrayList<>();
        sheets.sheetIterator().forEachRemaining(sheet -> parseSheet(path, resources, sheet));

        return resources;
    }

    // TODO extract Path to constructor
    private static void parseSheet(Path path, List<Resource> resources, Sheet sheet) {
        Row firstRow = sheet.getRow(0);
        if (rowIsEmpty(firstRow)) {
            return;
        }

        Header header = parseHeader(firstRow);
        int lastRowNum = getLastRowNum(sheet);

        for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
            appendRow(sheet, header, rowIndex);
        }

        String name = sheet.getSheetName();
        header.langToText.forEach((k, v) -> resources.add(
                Resource.fromString(
                        path,
                        k,
                        name,
                        v.toString()
                )
        ));
    }

    private static void appendRow(Sheet sheet, Header header, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        Set<String> firstCellOfLangVisited = new HashSet<>();

        for (int cellIndex = 0; cellIndex < header.columnsInfo.size(); cellIndex++) {
            ColumnInfo columnInfo = header.columnsInfo.get(cellIndex);
            Cell cell = row == null ? null : row.getCell(cellIndex);

            appendCell(header, columnInfo, cell, firstCellOfLangVisited);
        }

        for (StringBuilder text : header.allTexts()) {
            text.append("\r\n");
        }
    }

    private static void appendCell(Header header, ColumnInfo columnInfo, Cell cell, Set<String> firstCellOfLangVisited) {
        if (skipCell(columnInfo, cell)) {
            return;
        }

        Collection<String> langsToVisit = columnInfo.isCommon()
                ? header.allLanguages()
                : Collections.singletonList(columnInfo.lang);

        for (String lang : langsToVisit) {
            StringBuilder text = header.langToText.get(lang);

            if (!firstCellOfLangVisited.add(lang)) {
                text.append('\t');
            }

            if (cell == null) {
                continue;
            }

            text.append(getCellText(cell));
        }
    }

    private static boolean skipCell(ColumnInfo columnInfo, Cell cell) {
        switch (columnInfo.action) {
            case SKIP_ALWAYS:
                return true;
            case SKIP_EMPTY:
                return cell == null || cell.toString().isBlank();
            case APPEND_ALWAYS:
                return false;
        }
        throw new IllegalArgumentException("Wrong enum: " + columnInfo.action);
    }

    private static boolean rowIsEmpty(Row firstRow) {
        return firstRow == null
                || firstRow.getCell(0) == null
                || firstRow.getCell(0).toString().isBlank();
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

    private static Header parseHeader(Row firstRow) {
        Header header = new Header();

        for (int cellIndex = 0; cellIndex < firstRow.getLastCellNum(); cellIndex++) {
            ColumnInfo columnInfo = parseColumnInfo(firstRow.getCell(cellIndex));
            if (columnInfo == null) {
                break;
            }

            header.columnsInfo.add(columnInfo);

            if (columnInfo.action != Action.SKIP_ALWAYS && !columnInfo.isCommon()) {
                header.langToText.computeIfAbsent(columnInfo.lang, k -> new StringBuilder());
            }
        }

        return header;
    }

    private static ColumnInfo parseColumnInfo(Cell cell) {
        if (cell == null || cell.getCellType() != CellType.STRING || cell.getStringCellValue().isBlank()) {
            return null;
        }

        String lang = cell.toString();
        if (lang.equals("ignore")) {
            return new ColumnInfo(Action.SKIP_ALWAYS, null);
        }

        String[] langParts = lang.split(":");

        Action action = langParts.length > 1 && langParts[1].equals("not_blank")
                ? Action.SKIP_EMPTY
                : Action.APPEND_ALWAYS;

        return new ColumnInfo(action, langParts[0]);
    }

    private enum Action {
        SKIP_EMPTY, SKIP_ALWAYS, APPEND_ALWAYS
    }

    private static class ColumnInfo {
        final Action action;
        final String lang;

        ColumnInfo(Action action, String lang) {
            this.action = action;
            this.lang = lang;
        }

        boolean isCommon() {
            return "common".equals(lang);
        }

    }

    private static class Header {
        final List<ColumnInfo> columnsInfo = new ArrayList<>();
        final Map<String, StringBuilder> langToText = new HashMap<>();

        Collection<String> allLanguages() {
            return langToText.keySet();
        }

        Collection<StringBuilder> allTexts() {
            return langToText.values();
        }

    }

}
