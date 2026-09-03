package org.applecommander.fx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;

public class AppleCommanderFX extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        createWindow(stage, null);
    }

    public static void openNewWindow(File diskFile) {
        Stage stage = new Stage();
        try {
            createWindow(stage, diskFile);
            stage.toFront();
            stage.requestFocus();
        } catch (Exception ex) {
            throw new RuntimeException("Could not open new disk window", ex);
        }
    }

    private static void createWindow(Stage stage, File diskFile) throws Exception {
        FXMLLoader loader = new FXMLLoader(AppleCommanderFX.class.getResource("app.fxml"));
        Parent root = loader.load();

        DiskController controller = loader.getController();
        controller.setPrimaryStage(stage);

        Scene scene = new Scene(root, 1200, 700);
        scene.getStylesheets().addAll(
                AppleCommanderFX.class.getResource("theme-light.css").toExternalForm(),
                AppleCommanderFX.class.getResource("theme-dark.css").toExternalForm()
        );

        stage.setTitle("AppleCommanderFX");
        stage.setScene(scene);
        stage.show();

        if (diskFile != null) {
            controller.openDiskFile(diskFile, false);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
