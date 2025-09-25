package com.bizcon.taxesautomator.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Pattern;

public interface ApiUtils {

    default String generateRandomKey() {
        return java.util.UUID.randomUUID().toString().substring(0, 6);
    }

    default String normalizeDate(String inputDate){

        if (inputDate.indexOf("T") > 0) {
            inputDate = inputDate.substring(0, inputDate.indexOf("T"));
        }

        LocalDate date = LocalDate.parse(inputDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return date.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"));
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

    default String sanitizeForWindows(String original) {
        if (original == null || original.isBlank()) return "untitled";

        // Нормализуем Юникод (совместимость/составные символы)
        String name = Normalizer.normalize(original, Normalizer.Form.NFKC);

        // Убираем управляющие символы (ASCII < 32) и DEL
        name = name.replaceAll("\\p{Cntrl}", "");

        // Разделяем на имя и расширение, чтобы сохранить расширение при усечении
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            base = name.substring(0, dot);
            ext = name.substring(dot + 1);
        }

        // Заменяем запрещенные для Windows символы
        Pattern illegal = Pattern.compile("[\\\\/:*?\"<>|]");
        base = illegal.matcher(base).replaceAll("_");
        ext = illegal.matcher(ext).replaceAll("_");

        // Коллапсируем множественные пробелы/подчеркивания
        base = base.replaceAll("[\\s_]{2,}", "_").trim();

        // Убираем завершающие точки/пробелы (Windows это запрещает)
        base = base.replaceAll("[\\s.]+$", "");
        if (base.isBlank()) base = "untitled";

        // Защита от зарезервированных имен устройств (без учета регистра)
        Set<String> reserved = Set.of(
                "CON","PRN","AUX","NUL",
                "COM1","COM2","COM3","COM4","COM5","COM6","COM7","COM8","COM9",
                "LPT1","LPT2","LPT3","LPT4","LPT5","LPT6","LPT7","LPT8","LPT9",
                "CLOCK$"
        );
        String cmp = base.toUpperCase();
        if (reserved.contains(cmp)) base = base + "_";

        // Ограничиваем длину имени файла (с запасом под путь)
        final int MAX_FILE_NAME = 240; // безопасный предел для имени без пути
        String safeExt = ext.isBlank() ? "" : "." + ext;
        int maxBaseLen = Math.max(1, MAX_FILE_NAME - safeExt.length());
        if (base.length() > maxBaseLen) base = base.substring(0, maxBaseLen);

        // На всякий случай снова уберем завершающие точки/пробелы после усечения
        base = base.replaceAll("[\\s.]+$", "");
        if (base.isBlank()) base = "untitled";

        return base + safeExt;
    }
}
