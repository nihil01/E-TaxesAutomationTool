package com.bizcon.taxesautomator.services;

import com.bizcon.taxesautomator.models.Record;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExcelService {

    public static List<Record> readExcelFile(String path) throws IOException {
        System.out.println("Excel path");
        System.out.println(path);
        FileInputStream file = new FileInputStream(path);
        Workbook workbook = new XSSFWorkbook(file);
        System.out.println("yooooooooooo");
        List<Record> records = new ArrayList<>();
        Sheet sheet = workbook.getSheetAt(0);
        System.out.println(sheet);

        System.out.println(sheet.getSheetName());
        Iterator<Row> rowIterator = sheet.iterator();

        // Пропускаем заголовок
        if (rowIterator.hasNext()) rowIterator.next();

        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();

            Record record = new Record();
            record.setVoen(getCellValueAsString(row.getCell(0), workbook));
            record.setSearchStatus(getCellValueAsString(row.getCell(1), workbook));
            record.setAsanNomre(getCellValueAsString(row.getCell(2), workbook));
            record.setAsanId(getCellValueAsString(row.getCell(3), workbook));
            record.setOxunmamis(getCellValueAsString(row.getCell(4), workbook).equals("1"));
            record.setMakeReport(getCellValueAsString(row.getCell(5), workbook).equals("1"));
            record.setDetailsReport(getCellValueAsString(row.getCell(6), workbook).equals("1"));
            record.setBaslangicTarixi(getCellValueAsString(row.getCell(7), workbook));
            record.setBitmeTarixi(getCellValueAsString(row.getCell(8), workbook));

            records.add(record);

            System.out.println("Loaded record: " + record);
        }

        System.out.println("Processed records");
        System.out.println(records);
        workbook.close();
        file.close();
        return records;
    }

    private static String getCellValueAsString(Cell cell, Workbook workbook) {
        if (cell == null) return "";

        System.out.printf("Processing cell with type %s", cell.getCellType().toString());

        try {
            System.out.println(cell.getStringCellValue());
        } catch (Exception e) {
            System.out.println((long) cell.getNumericCellValue());
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat("dd.MM.yyyy").format(cell.getDateCellValue());
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                return getCellValueAsString(evaluator.evaluateInCell(cell), workbook);
            default:
                return "";
        }
    }

    public static void writeExcelFile(List<Record> records, String fileName) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet");

        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"voen", "search", "asan_phone", "asan_id", "mode",
                          "account", "details", "date_from", "date_to"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // Create data rows
        int rowNum = 1;
        for (Record record : records) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(record.getVoen());
            row.createCell(1).setCellValue(record.getSearchStatus());
            row.createCell(2).setCellValue(record.getAsanNomre());
            row.createCell(3).setCellValue(record.getAsanId());
            row.createCell(4).setCellValue(record.getOxunmamis().get() ? "1" : "0");
            row.createCell(5).setCellValue(record.getMakeReport().get() ? "1" : "0");
            row.createCell(6).setCellValue(record.getDetailsReport().get() ? "1" : "0");
            row.createCell(7).setCellValue(record.getBaslangicTarixi());
            row.createCell(8).setCellValue(record.getBitmeTarixi());
        }

        // Auto size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        //Save path
        String userHome = System.getProperty("user.home");
        Path downloadsPath = Paths.get(userHome ,"Downloads", fileName);

        // Write the workbook to file
        try (FileOutputStream fileOut = new FileOutputStream(downloadsPath.toFile())) {
            workbook.write(fileOut);
        }
        workbook.close();
    }
}
