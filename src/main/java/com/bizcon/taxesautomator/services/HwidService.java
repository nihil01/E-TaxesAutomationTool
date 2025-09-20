package com.bizcon.taxesautomator.services;

import com.bizcon.taxesautomator.controllers.MainController;
import com.bizcon.taxesautomator.utils.AlertorFX;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;

import java.io.IOException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import java.util.Base64;
import java.util.prefs.Preferences;

public class HwidService {


    private static final Preferences prefs = Preferences.userNodeForPackage(MainController.class);

    public static void registerApplicationActivation(){
        String activationCount = prefs.get("activationCount", null);
        System.out.println("Current activation counter: " + activationCount);
        if (activationCount == null){
            prefs.put("activationCount", "1");
        } else if (Integer.parseInt(activationCount) == 20) {
            //request license
            AlertorFX.show("Zehmet olmasa, lisenziya nomresi yeneden daxil edersiniz...", Alert.AlertType.INFORMATION);
            prefs.remove("activationCount");
            prefs.remove("licenseKey");

        } else {
            int count = Integer.parseInt(activationCount);
            prefs.put("activationCount", String.valueOf(count + 1));
        }

    }

    private static Map<String, String> generateHWID() {
        try {
            Map<String, String> result = new HashMap<>();

            SystemInfo si = new SystemInfo();

            String motherboardSerial = si.getHardware().getComputerSystem().getBaseboard().getSerialNumber();
            String sysUUID = si.getHardware().getComputerSystem().getHardwareUUID();

            // Проверяем "мусорные" значения и пустые строки
            if (motherboardSerial == null || motherboardSerial.isEmpty() ||
                    motherboardSerial.equalsIgnoreCase("To be filled by O.E.M")) {
                motherboardSerial = "UNKNOWN_MOTHERBOARD";
            }

            if (sysUUID == null || sysUUID.isEmpty() ||
                    sysUUID.equalsIgnoreCase("To be filled by O.E.M")) {
                sysUUID = "UNKNOWN_UUID";
            }

            // Берем первый диск как системный
            List<HWDiskStore> disks = si.getHardware().getDiskStores();
            String sysDriveSerial = "UNKNOWN_DISK";
            if (!disks.isEmpty()) {
                sysDriveSerial = disks.get(0).getSerial();
                if (sysDriveSerial == null || sysDriveSerial.isEmpty() ||
                        sysDriveSerial.equalsIgnoreCase("To be filled by O.E.M")) {
                    sysDriveSerial = "UNKNOWN_DISK";
                }
            }

            String rawOutput = String.join(";", motherboardSerial, sysUUID, sysDriveSerial);

            // Хэшируем SHA-256 + Base64
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawOutput.getBytes());
            String hashedOutput = Base64.getEncoder().encodeToString(hash);

            result.put("raw", rawOutput);
            result.put("hashed", hashedOutput);

            return result;

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            AlertorFX.show("Xəta: HWID generasiyası zamanı xəta baş verdi.", Alert.AlertType.ERROR);
            return null;
        }
    }



    public static boolean checkLicense(String license) {
        try {
            System.out.println("Checking license ");
            List<Map<String, String>> data = GoogleSheetsService.getLicences();
            System.out.println("Loaded licenses: " + data);

            for (Map<String, String> output : data) {
                if (output.isEmpty()) continue;

                String registeredHWID = output.get("hwid");
                String cellAddress = output.get("cellAddress");

                if (!output.containsValue(license)) continue;

                Map<String, String> myHWID = generateHWID();
                if (myHWID == null || myHWID.get("hashed") == null) {
                    AlertorFX.show("Xəta: Lisenziyanı yoxlayarkən xəta baş verdi.", Alert.AlertType.ERROR);
                    return false;
                }

                // ✅ Совпало
                if (myHWID.get("hashed").equals(registeredHWID)) {
                    AlertorFX.show("Lisenziya ugurla təsdiqləndi.", Alert.AlertType.INFORMATION);
                    prefs.put("licenseKey", license);
                    return true;
                }

                // 🆕 Если HWID пустой — регистрируем
                if (registeredHWID == null || registeredHWID.isEmpty()) {
                    LocalDateTime now = LocalDateTime.now();
                    String formattedDate = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));

                    GoogleSheetsService.updateCellValue("E" + cellAddress, myHWID.get("raw"));
                    GoogleSheetsService.updateCellValue("B" + cellAddress, myHWID.get("hashed"));
                    GoogleSheetsService.updateCellValue("D" + cellAddress, formattedDate);

                    System.out.println("License key has been registered!");
                    AlertorFX.show("Lisenziya uğurla qeydiyyata alındı. Restart programi", Alert.AlertType.INFORMATION);
                    prefs.put("licenseKey", license);
                    Platform.exit();
                    System.exit(0);
                    return true;
                }
                // ❌ Занята другим ПК
                AlertorFX.show("Xəta: Lisenziya başqa kompüterdə qeydiyyatdan keçib.", Alert.AlertType.ERROR);
                return false;
            }

            // Не нашли
            AlertorFX.show("Xəta: Lisenziya açarı etibarsızdır.", Alert.AlertType.ERROR);
            return false;

        } catch (GeneralSecurityException | IOException e) {

            if (e instanceof UnknownHostException){
                AlertorFX.show("Xəta: Sebeke qosulmani yoxlayin", Alert.AlertType.ERROR);

            }else{
                AlertorFX.show("Xəta: Lisenziyanı yoxlayarkən xəta baş verdi.", Alert.AlertType.ERROR);
            }
            System.err.println(e.getMessage());
            return false;
        }
    }
}
