package com.bizcon.taxesautomator.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;

import java.io.*;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.*;

public class GoogleSheetsService {

    private static final String APPLICATION_NAME = "ETaxesAutomator";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String spreadsheetId = "1oUtQ6HTjRpxBOsFf6UMOtpcX48i_bXceLsdeSnLMmfo";


    public static Sheets getService() throws IOException, GeneralSecurityException {
        var HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        var credentials = ServiceAccountCredentials
                .fromStream(Objects.requireNonNull(GoogleSheetsService.class
                        .getResourceAsStream("/creds/credentials.json")))
                .createScoped(List.of(SheetsScopes.SPREADSHEETS));

        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static List<Map<String, String>> getLicences() throws IOException, GeneralSecurityException, UnknownHostException {
        var licenseKeys = new ArrayList<Map<String, String>>();
        final String range = "LICENSE_LIST!A2:D";

        Sheets service = getService();

        Spreadsheet sheet = service.spreadsheets()
                .get(spreadsheetId)
                .setRanges(List.of(range))
                .setIncludeGridData(true)
                .execute();

        var gridData = sheet.getSheets().get(0).getData().get(0).getRowData();

    for (int rowIndex = 0; rowIndex < gridData.size(); rowIndex++) {

            var license = new HashMap<String, String>();

            var row = gridData.get(rowIndex);
            if (row.getValues() == null || row.getValues().size() < 3) continue;

            var cell = row.getValues().get(0);
            if (cell == null || cell.getFormattedValue() == null) continue;

            String value = cell.getFormattedValue();
            int cellAddress = rowIndex+2;

            license.put("license", value);
            license.put("cellAddress", String.valueOf(cellAddress));
            license.put("hwid", row.getValues().get(1).getFormattedValue());


            licenseKeys.add(license);
            System.out.println(licenseKeys);
        }

        return licenseKeys;
    }

    public static void updateCellValue(String range, String newValue) throws IOException, GeneralSecurityException {
        Sheets service = getService();

        ValueRange body = new ValueRange()
                .setValues(List.of(
                        Collections.singletonList(newValue)
                ));

        service.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute();


        System.out.println("Data updated!");
    }
}