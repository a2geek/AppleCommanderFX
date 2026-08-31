package org.applecommander.fx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/applecommander/fx/app.fxml"));
        Parent root = loader.load();

        AppController controller = loader.getController();
        controller.setPrimaryStage(stage);

        stage.setTitle("AppleCommanderFX");
        stage.setScene(new Scene(root, 1100, 700));
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.show();
    }
}
