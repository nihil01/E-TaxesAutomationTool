module com.bizcon.taxesautomator {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;
    requires java.logging;
    requires java.prefs;
    requires javafx.graphics;
    requires java.net.http;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires static lombok;
    requires com.google.api.client;
    requires com.google.api.client.json.gson;
    requires google.api.services.sheets.v4.rev614;
    requires com.google.api.client.auth;
    requires google.api.client;
    requires com.google.auth.oauth2;
    requires com.google.auth;
    requires com.github.oshi;
    requires org.apache.poi.ooxml;

    opens com.bizcon.taxesautomator to javafx.fxml;
    exports com.bizcon.taxesautomator;
    exports com.bizcon.taxesautomator.controllers;
    opens com.bizcon.taxesautomator.controllers to javafx.fxml;

    exports com.bizcon.taxesautomator.models;
    opens com.bizcon.taxesautomator.models to javafx.base, javafx.fxml;
}