package com.bizcon.taxesautomator.utils;

import javafx.scene.control.Alert;

public class AlertorFX {

    public static void show(String message, Alert.AlertType alertType){

        Alert alert = new Alert(alertType);

        alert.setTitle("Diqqet!");

        alert.setHeaderText(null);

        alert.setContentText(message);

        alert.showAndWait();

    }


}