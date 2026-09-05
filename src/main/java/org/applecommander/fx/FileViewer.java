package org.applecommander.fx;

import com.jthemedetecor.OsThemeDetector;
import com.webcodepro.applecommander.storage.FileEntry;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FileViewer {
    private static final Map<Stage, Set<Stage>> OWNER_WINDOWS = new HashMap<>();

    public static void open(FileEntry fileEntry, Stage owner) {
        if (fileEntry == null) {
            return;
        }

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("File Viewer - " + fileEntry.getFilename());

        FXMLLoader loader = new FXMLLoader(FileViewer.class.getResource("/org/applecommander/fx/FileViewer.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        FileViewerController controller = loader.getController();
        controller.init(fileEntry);

        Scene scene = new Scene(root, 850, 600);
        applyTheme(scene);
        OsThemeDetector.getDetector().registerListener(isDark -> javafx.application.Platform.runLater(() -> applyTheme(scene)));
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> closeOwnerWindows(stage));
        stage.setOnHidden(event -> closeOwnerWindows(stage));

        if (owner != null) {
            owner.setOnHidden(event -> closeWindowsForOwner(owner));
            OWNER_WINDOWS.computeIfAbsent(owner, ignored -> new HashSet<>()).add(stage);
        }

        stage.show();
    }

    private static void applyTheme(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().clear();
        String cssPath = OsThemeDetector.getDetector().isDark()
                ? "/org/applecommander/fx/theme-dark.css"
                : "/org/applecommander/fx/theme-light.css";
        scene.getStylesheets().add(FileViewer.class.getResource(cssPath).toExternalForm());
    }

    private static void closeOwnerWindows(Stage stage) {
        Stage owner = (Stage) stage.getOwner();
        if (owner != null) {
            closeWindowsForOwner(owner);
        }
    }

    private static void closeWindowsForOwner(Stage owner) {
        Set<Stage> windows = OWNER_WINDOWS.get(owner);
        if (windows == null) {
            return;
        }

        for (Stage child : new HashSet<>(windows)) {
            child.close();
        }
        OWNER_WINDOWS.remove(owner);
    }
}
