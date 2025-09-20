package com.bizcon.taxesautomator.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MessageDto {

    private Type type;
    private String targetType;
    private String subTypeCode;
    private Category category;
    private Recipient recipient;
    private PaperCopyOfAnswer paperCopyOfAnswer;
    private Sender sender;
    private String id;
    private String registerNumber;
    private String newRegisterNumber;
    private String executor;
    private Manager manager;
    private String createdAt;
    private List<Object> history;   // если появится структура – вынеси в отдельный класс
    private String subject;
    private String content;
    private String thread;
    private List<FileDto> files;
    private List<Object> applications;
    private List<Object> invoices;
    private List<Object> taxReturns;
    private List<Object> relatedMessages;
    private String readDate;
    private boolean isPrintable;
    private String messageAction;
    private boolean outDated;

    @Data
    public static class Type {
        private String code;
        private LocalizedName name;

        // getters/setters
    }

    @Data
    public static class LocalizedName {
        private String az;
        private String en;
        private String ru;

        // getters/setters
    }

    @Data
    public static class Category {
        private String code;
        private String name;

        // getters/setters
    }

    @Data
    public static class Recipient {
        private String kind;
        private String tin;
        private String taxAuthorityCode;
        private LocalizedName name;
        private String fin;

        // getters/setters
    }

    @Data
    public static class PaperCopyOfAnswer {
        private String kind;
        private String address;
        private String serviceCenterCode;

        // getters/setters
    }

    @Data
    public static class Sender {
        private String kind;
        private String tin;
        private String taxAuthorityCode;
        private LocalizedName name;
        private String fin;

        // getters/setters
    }

    @Data
    public static class Manager {
        private String name;
        private String position;

        // getters/setters
    }

    @Data
    public static class FileDto {
        private String id;
        private String name;
        private String kind;
        private long size;

        // getters/setters
    }
}
