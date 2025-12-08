package com.bizcon.taxesautomator.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MessageDto {

    private String subject;
    private String content;
    private List<FileDto> files;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileDto {
        private String id;
        private String name;
    }
}
