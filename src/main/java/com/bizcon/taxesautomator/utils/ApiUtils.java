package com.bizcon.taxesautomator.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface ApiUtils {

    default String generateRandomKey() {
        return java.util.UUID.randomUUID().toString().substring(0, 6);
    }

    default Path createUploadDir(String path, String searchElement) {

        Path dirPath = Paths.get(path, searchElement);

        if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
            return dirPath;
        }else{

            try {
                Files.createDirectory(dirPath);
                return dirPath;

            } catch (IOException e) {
                System.out.println(e.getMessage());
                return null;
            }

        }
    }
}
