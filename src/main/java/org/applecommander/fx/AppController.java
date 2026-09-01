package org.applecommander.fx;

import com.webcodepro.applecommander.storage.DiskException;
import com.webcodepro.applecommander.storage.Disks;
import com.webcodepro.applecommander.storage.FileEntry;
import com.webcodepro.applecommander.storage.FormattedDisk;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.applecommander.source.FileSource;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;

public class AppController {
    private static final String PREF_NODE = "/org/applecommander/fx";
    private static final String IMAGE_DIRECTORY_KEY = "image_directory";

    @FXML private TableView<DiskFileRow> fileTable;
    @FXML private TableColumn<DiskFileRow, String> nameColumn;
    @FXML private TableColumn<DiskFileRow, String> typeColumn;
    @FXML private TableColumn<DiskFileRow, Number> sizeColumn;
    @FXML private TableColumn<DiskFileRow, String> statusColumn;
    @FXML private Label statusLabel;

    private Stage primaryStage;
    private FormattedDisk currentDisk;

    @FXML
    private void initialize() {
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        fileTable.setItems(FXCollections.emptyObservableList());

        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type()));
        sizeColumn.setCellValueFactory(cell -> new SimpleLongProperty(cell.getValue().size()));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().locked() ? "Locked" : (cell.getValue().deleted() ? "Deleted" : "Ready")
        ));
        sizeColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @FXML
    private void openDisk() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Apple II disk image");
        File lastDirectory = getLastOpenedDirectory();
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            fileChooser.setInitialDirectory(lastDirectory);
        }
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Disk images", "*.dsk", "*.img", "*.po", "*.nib", "*.2mg", "*.woz", "*.hdv"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile == null) {
            return;
        }

        try {
            saveLastOpenedDirectory(selectedFile.getParentFile());
            FormattedDisk disk = Disks.inspect(new FileSource(selectedFile.toPath())).disks.get(0);
            displayDisk(disk);
            primaryStage.setTitle("AppleCommanderFX - " + selectedFile.getName());
        } catch (Exception ex) {
            showErrorDialog("Could not open disk image", ex);
        }
    }

    @FXML
    private void closeDisk() {
        currentDisk = null;
        fileTable.setItems(FXCollections.emptyObservableList());
        statusLabel.setText("No disk image opened.");
        if (primaryStage != null) {
            primaryStage.setTitle("AppleCommanderFX");
        }
    }

    @FXML
    private void exitApplication() {
        Platform.exit();
    }

    private void displayDisk(FormattedDisk disk) {
        currentDisk = disk;

        try {
            List<DiskFileRow> rows = disk.getFiles().stream()
                    .map(AppController::toDiskFileRow)
                    .sorted(Comparator.comparing(DiskFileRow::name))
                    .toList();

            fileTable.setItems(FXCollections.observableArrayList(rows));
            statusLabel.setText("Open disk: " + disk.getDiskName() + " (" + disk.getFormat() + ")");
        } catch (DiskException ex) {
            currentDisk = null;
            showErrorDialog("Could not read files from disk image", ex);
            fileTable.setItems(FXCollections.emptyObservableList());
            statusLabel.setText("No disk image opened.");
        }
    }

    private static DiskFileRow toDiskFileRow(FileEntry fileEntry) {
        return new DiskFileRow(
                fileEntry.getFilename(),
                fileEntry.getFiletype(),
                fileEntry.getSize(),
                fileEntry.isLocked(),
                fileEntry.isDeleted() || fileEntry.isDirectory()
        );
    }

    private File getLastOpenedDirectory() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        String directoryPath = prefs.get(IMAGE_DIRECTORY_KEY, null);
        if (directoryPath == null || directoryPath.isBlank()) {
            return null;
        }

        File directory = new File(directoryPath);
        return directory.isDirectory() ? directory : null;
    }

    private void saveLastOpenedDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }

        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        prefs.put(IMAGE_DIRECTORY_KEY, directory.getAbsolutePath());
    }

    private void showErrorDialog(String message, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Disk Browser Error");
        alert.setHeaderText(message);
        alert.setContentText(ex.getMessage() == null ? "An unexpected error occurred." : ex.getMessage());
        alert.showAndWait();
    }

    public record DiskFileRow(String name, String type, long size, boolean locked, boolean deleted) {
    }
}
