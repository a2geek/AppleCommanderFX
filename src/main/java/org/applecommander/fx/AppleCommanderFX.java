package org.applecommander.fx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AppleCommanderFX extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("app.fxml"));
        Parent root = loader.load();

        DiskController controller = loader.getController();
        controller.setPrimaryStage(stage);

        Scene scene = new Scene(root, 1200, 700);
        scene.getStylesheets().addAll(
                getClass().getResource("theme-light.css").toExternalForm(),
                getClass().getResource("theme-dark.css").toExternalForm()
        );

        stage.setTitle("AppleCommanderFX");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
