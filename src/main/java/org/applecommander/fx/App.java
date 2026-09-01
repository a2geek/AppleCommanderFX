package org.applecommander.fx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

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

        Scene scene = new Scene(root, 1100, 700);
        applySystemTheme(scene);

        stage.setTitle("AppleCommanderFX");
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.show();
    }

    private void applySystemTheme(Scene scene) {
        String cssPath = isDarkModeEnabled() ? "/org/applecommander/fx/theme-dark.css" : "/org/applecommander/fx/theme-light.css";
        scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());
    }

    private boolean isDarkModeEnabled() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        try {
            if (os.contains("mac")) {
                return readProcessOutput("defaults", "read", "-g", "AppleInterfaceStyle")
                        .contains("Dark");
            }
            if (os.contains("win")) {
                String result = readProcessOutput("cmd", "/c", "reg", "query", "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme");
                return result.contains("0x0") || result.contains("0x00");
            }
            if (os.contains("linux")) {
                String result = readProcessOutput("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
                return result.contains("dark");
            }
        } catch (Exception ignored) {
            // Fall back to the light theme when detection is unavailable.
        }

        return false;
    }

    private String readProcessOutput(String... command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
            return output.toString();
        }
    }
}
