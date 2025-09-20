package com.bizcon.taxesautomator.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // игнорируем лишние поля
public class DocumentModel {

    @JsonProperty("messages")
    private List<Document> messages;

    @JsonProperty("hasMore")
    private Boolean hasMore;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        private String id;
        private String categoryCode;
        private String typeCode;
        private Boolean isRead;
        private String registerNumber;
        private String newRegisterNumber;
        private String createdAt;
        private String stateId;
        private String deadlineDate;
        private Correspondent correspondent;
        private String subject;
        private Boolean hasAttachment;
        private String thread;
        private String kargnum;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Correspondent {
        private String kind;
        private String tin;
        private String taxAuthorityCode;
        private Map<String, String> name; // {az, en, ru}
        private String fin;
    }
}
