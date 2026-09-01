package org.applecommander.fx;

import com.webcodepro.applecommander.storage.DiskException;
import com.webcodepro.applecommander.storage.Disks;
import com.webcodepro.applecommander.storage.FormattedDisk;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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
    @FXML private Label statusLabel;
    @FXML private ToggleButton standardToolButton;
    @FXML private ToggleButton nativeToolButton;
    @FXML private ToggleButton detailToolButton;

    private Stage primaryStage;
    private FormattedDisk currentDisk;
    private int currentDisplayMode = FormattedDisk.FILE_DISPLAY_STANDARD;

    @FXML
    private void initialize() {
        fileTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        fileTable.setItems(FXCollections.emptyObservableList());
        applyViewMode(currentDisplayMode);
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
        fileTable.getColumns().clear();
        statusLabel.setText("No disk image opened.");
        if (primaryStage != null) {
            primaryStage.setTitle("AppleCommanderFX");
        }
    }

    @FXML
    private void exitApplication() {
        Platform.exit();
    }

    @FXML
    private void selectStandardView() {
        applyViewMode(FormattedDisk.FILE_DISPLAY_STANDARD);
    }

    @FXML
    private void selectNativeView() {
        applyViewMode(FormattedDisk.FILE_DISPLAY_NATIVE);
    }

    @FXML
    private void selectDetailView() {
        applyViewMode(FormattedDisk.FILE_DISPLAY_DETAIL);
    }

    private void applyViewMode(int displayMode) {
        this.currentDisplayMode = displayMode;
        if (standardToolButton != null) {
            standardToolButton.setSelected(displayMode == FormattedDisk.FILE_DISPLAY_STANDARD);
        }
        if (nativeToolButton != null) {
            nativeToolButton.setSelected(displayMode == FormattedDisk.FILE_DISPLAY_NATIVE);
        }
        if (detailToolButton != null) {
            detailToolButton.setSelected(displayMode == FormattedDisk.FILE_DISPLAY_DETAIL);
        }

        if (currentDisk != null) {
            refreshDiskView();
        }
    }

    private void displayDisk(FormattedDisk disk) {
        currentDisk = disk;
        refreshDiskView();
    }

    private void refreshDiskView() {
        if (currentDisk == null) {
            return;
        }

        try {
            populateDiskRows(currentDisk, currentDisplayMode);
            statusLabel.setText("Open disk: " + currentDisk.getDiskName() + " (" + currentDisk.getFormat() + ")");
        } catch (DiskException ex) {
            currentDisk = null;
            showErrorDialog("Could not read files from disk image", ex);
            fileTable.setItems(FXCollections.emptyObservableList());
            fileTable.getColumns().clear();
            statusLabel.setText("No disk image opened.");
        }
    }

    private void populateDiskRows(FormattedDisk disk, int displayMode) throws DiskException {
        fileTable.getColumns().clear();
        List<FormattedDisk.FileColumnHeader> headers = disk.getFileColumnHeaders(displayMode);

        for (int i = 0; i < headers.size(); i++) {
            final int columnIndex = i;
            FormattedDisk.FileColumnHeader header = headers.get(i);
            TableColumn<DiskFileRow, String> column = new TableColumn<>(header.getTitle());
            column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().values().get(columnIndex)));
            column.setMinWidth(60);
            column.setPrefWidth(Math.max(80, Math.min(220, header.getMaximumWidth() * 7)));
            column.setMaxWidth(400);
            if (header.isRightAlign()) {
                column.setStyle("-fx-alignment: CENTER-RIGHT;");
            } else if (header.isCenterAlign()) {
                column.setStyle("-fx-alignment: CENTER;");
            }
            fileTable.getColumns().add(column);
        }

        List<DiskFileRow> rows = disk.getFiles().stream()
                .map(fileEntry -> new DiskFileRow(fileEntry.getFileColumnData(displayMode)))
                .sorted(Comparator.comparing(row -> row.values().isEmpty() ? "" : row.values().get(0)))
                .toList();

        fileTable.setItems(FXCollections.observableArrayList(rows));
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

    public record DiskFileRow(List<String> values) {
    }
}
