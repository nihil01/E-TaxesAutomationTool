package com.bizcon.taxesautomator.services;

import com.bizcon.taxesautomator.models.Record;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
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
            record.setBaslangicTarixi(getCellValueAsString(row.getCell(6), workbook));
            record.setBitmeTarixi(getCellValueAsString(row.getCell(7), workbook));

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



}
