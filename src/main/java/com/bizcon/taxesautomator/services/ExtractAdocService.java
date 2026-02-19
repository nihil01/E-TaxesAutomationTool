package com.bizcon.taxesautomator.services;

import com.bizcon.taxesautomator.utils.MessageType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ExtractAdocService {

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }


    public static void unzip(File filePath, File destDir){

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(filePath))){

            LoggingService.logData("Trying to extract %s to %s".formatted(filePath.getName()
                    , destDir.getName()), MessageType.INFO);

            ZipEntry zipEntry;

            while ((zipEntry = zis.getNextEntry()) != null) {

                String entryName = zipEntry.getName();

                // пропускаем META-INF и всё внутри неё
                if (entryName.startsWith("META-INF/") || entryName.equalsIgnoreCase("mimetype")) {
                    zis.closeEntry();
                    continue;
                }

                File newFile = newFile(destDir, zipEntry);

                if (zipEntry.isDirectory()) {

                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }

                } else {

                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }

                zis.closeEntry();
            }


        } catch (IOException e) {
            LoggingService.logData(e.getMessage(), MessageType.ERROR);
        }

    }

}
