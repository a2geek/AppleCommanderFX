package org.applecommander.fx;

import com.webcodepro.applecommander.storage.DiskException;
import com.webcodepro.applecommander.storage.Disks;
import com.webcodepro.applecommander.storage.FileEntry;
import com.webcodepro.applecommander.storage.FormattedDisk;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.applecommander.source.FileSource;

import java.io.File;
import java.util.Comparator;
import java.util.List;

public class App extends Application {
    private final TableView<DiskFileRow> fileTable = new TableView<>();
    private final Label statusLabel = new Label("No disk image opened.");
    private Stage primaryStage;
    private FormattedDisk currentDisk;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        stage.setTitle("AppleCommanderFX");
        stage.setScene(new Scene(buildScene(), 1100, 700));
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.show();
    }

    private Parent buildScene() {
        BorderPane root = new BorderPane();
        root.setTop(createMenuBar());
        root.setCenter(fileTable);
        root.setBottom(statusLabel);
        BorderPane.setMargin(statusLabel, new Insets(8, 12, 8, 12));

        fileTable.setPlaceholder(new Label("No disk image open. Use File > Open to browse a disk image."));
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<DiskFileRow, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        nameColumn.setPrefWidth(360);

        TableColumn<DiskFileRow, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type()));
        typeColumn.setPrefWidth(140);

        TableColumn<DiskFileRow, Number> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(cell -> new SimpleLongProperty(cell.getValue().size()));
        sizeColumn.setPrefWidth(120);
        sizeColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<DiskFileRow, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().locked() ? "Locked" : (cell.getValue().deleted() ? "Deleted" : "Ready")));
        statusColumn.setPrefWidth(110);

        fileTable.getColumns().setAll(nameColumn, typeColumn, sizeColumn, statusColumn);
        fileTable.setItems(FXCollections.emptyObservableList());

        return root;
    }

    private MenuBar createMenuBar() {
        Menu fileMenu = new Menu("File");

        MenuItem openItem = new MenuItem("Open...");
        openItem.setOnAction(event -> openDisk());

        MenuItem closeItem = new MenuItem("Close");
        closeItem.setOnAction(event -> closeDisk());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(event -> Platform.exit());

        fileMenu.getItems().addAll(openItem, closeItem, exitItem);
        return new MenuBar(fileMenu);
    }

    private void openDisk() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Apple II disk image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Disk images", "*.dsk", "*.img", "*.po", "*.nib", "*.2mg", "*.woz", "*.hdv"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile == null) {
            return;
        }

        try {
            FormattedDisk disk = Disks.inspect(new FileSource(selectedFile.toPath())).disks.get(0);
            displayDisk(disk);
            primaryStage.setTitle("AppleCommanderFX - " + selectedFile.getName());
        } catch (Exception ex) {
            showErrorDialog("Could not open disk image", ex);
        }
    }

    private void closeDisk() {
        currentDisk = null;
        fileTable.setItems(FXCollections.emptyObservableList());
        statusLabel.setText("No disk image opened.");
        primaryStage.setTitle("AppleCommanderFX");
    }

    private void displayDisk(FormattedDisk disk) {
        currentDisk = disk;

        try {
            List<DiskFileRow> rows = disk.getFiles().stream()
                    .map(App::toDiskFileRow)
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

    private void showErrorDialog(String message, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Disk Browser Error");
        alert.setHeaderText(message);
        alert.setContentText(ex.getMessage() == null ? "An unexpected error occurred." : ex.getMessage());
        alert.showAndWait();
    }

    private record DiskFileRow(String name, String type, long size, boolean locked, boolean deleted) {
    }
}
