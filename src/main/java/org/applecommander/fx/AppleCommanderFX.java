package org.applecommander.fx;

import com.jthemedetecor.OsThemeDetector;
import javafx.application.Application;
import javafx.application.Platform;
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
        FXMLLoader loader = new FXMLLoader(AppleCommanderFX.class.getResource("DiskViewer.fxml"));
        Parent root = loader.load();

        DiskViewerController controller = loader.getController();
        controller.setPrimaryStage(stage);

        Scene scene = new Scene(root, 1200, 700);
        applyTheme(scene);
        OsThemeDetector.getDetector().registerListener(isDark -> Platform.runLater(() -> applyTheme(scene)));

        stage.setTitle("AppleCommanderFX");
        stage.setScene(scene);
        stage.show();

        if (diskFile != null) {
            controller.openDiskFile(diskFile, false);
        }
    }

    private static void applyTheme(Scene scene) {
        scene.getStylesheets().clear();
        String cssPath = OsThemeDetector.getDetector().isDark()
                ? "/org/applecommander/fx/theme-dark.css"
                : "/org/applecommander/fx/theme-light.css";
        scene.getStylesheets().add(AppleCommanderFX.class.getResource(cssPath).toExternalForm());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
