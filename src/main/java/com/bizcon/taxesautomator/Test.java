package com.bizcon.taxesautomator;

import com.bizcon.taxesautomator.models.Record;
import com.bizcon.taxesautomator.services.ExcelService;

import java.io.IOException;
import java.util.List;

public class Test {

    public static void main(String[] args) throws IOException {


        List<Record> recordList = ExcelService.readExcelFile("C:\\Users\\Orkhan\\Documents\\TestExcel.xlsx");
        System.out.println(recordList);

    }

}
