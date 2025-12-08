package com.bizcon.taxesautomator;
import com.bizcon.taxesautomator.controllers.MainController;
import com.bizcon.taxesautomator.services.HwidService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;
import java.io.IOException;
import java.util.prefs.Preferences;

public class ETaxesApplication extends Application {

    private final Preferences prefs = Preferences.userNodeForPackage(MainController.class);

    @Override
public void start(Stage stage) throws IOException {
        Scene scene;
//        System.out.println(getClass().getResource("/images/vergi.png"));
//        Image icon = new Image(getClass().getResource("/images/vergi.png").toExternalForm());

//        stage.getIcons().add(icon);
        stage.setTitle("E-TaxesAutoTool");
        stage.setResizable(true);

        if (!checkLicense()){
            FXMLLoader fxmlLoader = new FXMLLoader(Launcher.class.getResource("license-page.fxml"));
            scene = new Scene(fxmlLoader.load());

            System.out.println("locationg license page...");
        }else {
            FXMLLoader fxmlLoader = new FXMLLoader(Launcher.class.getResource("main-page.fxml"));
            scene = new Scene(fxmlLoader.load());

            System.out.println("locationg main page...");
        }

        scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        stage.setScene(scene);

        // Set full screen before showing the stage
        stage.setMaximized(true);
        stage.show();

        HwidService.registerApplicationActivation();
}

    public boolean checkLicense(){
        System.out.println("Verifying license key ...");
        String licenseKey = prefs.get("licenseKey", null);
        System.out.println("My key");
        System.out.println(licenseKey);


        if (licenseKey == null) {
            return false;

        }else{
            return HwidService.checkLicense(licenseKey);
        }

    }
}
