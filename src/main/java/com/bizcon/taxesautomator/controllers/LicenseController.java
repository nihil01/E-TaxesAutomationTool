package com.bizcon.taxesautomator.controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.awt.*;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

import static com.bizcon.taxesautomator.services.HwidService.checkLicense;

public class LicenseController implements Initializable {

    @FXML private TextField licenseField;
    @FXML private Button checkButton;
//    @FXML private ImageView logoImage;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
//        Image image = new Image(Objects.requireNonNull(LicenseController.class.getResource("/images/vergi.png")).toExternalForm());

//        if (image != null) {
//            System.out.println("Image loaded successfully.");
//            logoImage.setImage(image);
//        } else {
//            System.out.println("Failed to load the image.");
//        }

        checkButton.setOnMouseClicked(mouseEvent -> {

            String appliedLicense = licenseField.getText().trim();
            checkLicense(appliedLicense);

        });

    }


}
