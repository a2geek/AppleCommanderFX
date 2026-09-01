package org.applecommander.fx;

import com.jthemedetecor.OsThemeDetector;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {
    private Scene scene;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/applecommander/fx/app.fxml"));
        Parent root = loader.load();

        AppController controller = loader.getController();
        controller.setPrimaryStage(stage);

        scene = new Scene(root, 1100, 700);
        applyTheme();

        final OsThemeDetector detector = OsThemeDetector.getDetector();
        detector.registerListener(isDark -> Platform.runLater(this::applyTheme));

        stage.setTitle("AppleCommanderFX");
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.show();
    }

    private void applyTheme() {
        if (scene == null) {
            return;
        }

        scene.getStylesheets().clear();
        String cssPath = OsThemeDetector.getDetector().isDark() ?
                "/org/applecommander/fx/theme-dark.css" :
                "/org/applecommander/fx/theme-light.css";
        scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());
    }
}
