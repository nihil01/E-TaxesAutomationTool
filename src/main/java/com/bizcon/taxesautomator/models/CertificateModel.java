package com.bizcon.taxesautomator.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
public class CertificateModel {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class CertificatesResponse {
        private List<Certificate> certificates;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Certificate {
        private String taxpayerType;
        private LegalInfo legalInfo;
        private IndividualInfo individualInfo;
        private String position;
        private boolean hasAccess;
        private boolean liquidated;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class LegalInfo {
        private String tin;
        private String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class IndividualInfo {
        private String fin;
        private String name;
    }


}
