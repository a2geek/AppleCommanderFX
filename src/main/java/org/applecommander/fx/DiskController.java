package org.applecommander.fx;

import com.jthemedetecor.OsThemeDetector;
import com.webcodepro.applecommander.storage.*;
import com.webcodepro.applecommander.storage.os.prodos.ProdosFormatDisk;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.applecommander.source.FileSource;

import java.io.File;
import java.util.List;
import java.util.prefs.Preferences;

public class DiskController {
    private static final String PREF_NODE = "/org/applecommander/fx";
    private static final String IMAGE_DIRECTORY_KEY = "image_directory";

    private static final int CONTENT_FILES = 0;
    private static final int CONTENT_DISK_USAGE = 1;

    @FXML private TableView<DiskFileRow> fileTable;
    @FXML private Label statusLabel;
    @FXML private ToggleButton filesContentButton;
    @FXML private ToggleButton diskUsageContentButton;
    @FXML private ToggleButton standardToolButton;
    @FXML private ToggleButton nativeToolButton;
    @FXML private ToggleButton detailToolButton;
    @FXML private ToggleButton deletedFilesToggleButton;
    @FXML private javafx.scene.image.ImageView deletedFilesIcon;
    @FXML private Button switchDiskButton;
    @FXML private RadioMenuItem standardViewMenuItem;
    @FXML private RadioMenuItem nativeViewMenuItem;
    @FXML private RadioMenuItem detailViewMenuItem;
    @FXML private javafx.scene.layout.HBox breadcrumbBar;

    // Disk usage UI
    @FXML private javafx.scene.layout.VBox diskUsagePane;
    @FXML private javafx.scene.canvas.Canvas diskUsageCanvas;
    @FXML private javafx.scene.layout.HBox legendBox;

    private Stage primaryStage;
    private List<FormattedDisk> availableDisks = new java.util.ArrayList<>();
    private int currentDiskIndex = -1;
    private FormattedDisk currentDisk;
    private DirectoryEntry currentDirectory;
    private List<DirectoryEntry> directoryPath = new java.util.ArrayList<>();
    private int currentContentMode = CONTENT_FILES;
    private int currentDisplayMode = FormattedDisk.FILE_DISPLAY_STANDARD;
    private boolean showDeletedFiles = false;

    @FXML
    private void initialize() {
        fileTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        fileTable.setItems(FXCollections.emptyObservableList());
        fileTable.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2) {
                return;
            }
            DiskFileRow selectedRow = fileTable.getSelectionModel().getSelectedItem();
            if (selectedRow == null || selectedRow.fileEntry() == null) {
                return;
            }
            FileEntry entry = selectedRow.fileEntry();
            if (entry.isDirectory() && entry instanceof DirectoryEntry directoryEntry) {
                navigateToDirectory(directoryEntry);
            }
        });
        setDeletedFilesButtonState();
        setContentControlsEnabled(false);
        setViewControlsEnabled(false);
        updateSwitchDiskButton();
        applyContentMode(currentContentMode);
        applyViewMode(currentDisplayMode);

        // Bind canvas size to the table area so the disk usage can reuse available space
        if (diskUsageCanvas != null && fileTable != null) {
            diskUsageCanvas.widthProperty().bind(fileTable.widthProperty());
            diskUsageCanvas.heightProperty().bind(fileTable.heightProperty().subtract(60));
        }
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

        openDiskFile(selectedFile, true);
    }

    public void openDiskFile(File selectedFile, boolean promptForWindow) {
        if (selectedFile == null) {
            return;
        }

        if (promptForWindow && currentDisk != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.initOwner(primaryStage);
            alert.setTitle("Open disk image");
            alert.setHeaderText("A disk image is already open.");
            alert.setContentText("Would you like to open this disk in the current window or in a new window?");
            ButtonType newWindow = new ButtonType("New Window", ButtonBar.ButtonData.YES);
            ButtonType thisWindow = new ButtonType("This Window", ButtonBar.ButtonData.NO);
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(thisWindow, newWindow, cancel);

            java.util.Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() == cancel) {
                return;
            }
            if (result.get() == newWindow) {
                AppleCommanderFX.openNewWindow(selectedFile);
                return;
            }
        }

        try {
            saveLastOpenedDirectory(selectedFile.getParentFile());
            var inspected = Disks.inspect(new FileSource(selectedFile.toPath()));
            availableDisks = inspected.disks;
            currentDiskIndex = availableDisks.isEmpty() ? -1 : 0;
            if (availableDisks.isEmpty()) {
                closeDisk();
                return;
            }
            displayDisk(availableDisks.get(currentDiskIndex));
            if (primaryStage != null) {
                primaryStage.setTitle("AppleCommanderFX - " + selectedFile.getName());
            }
        } catch (Exception ex) {
            showErrorDialog("Could not open disk image", ex);
        }
    }

    @FXML
    private void switchDisk() {
        if (availableDisks == null || availableDisks.size() <= 1) {
            return;
        }
        currentDiskIndex = (currentDiskIndex + 1) % availableDisks.size();
        FormattedDisk nextDisk = availableDisks.get(currentDiskIndex);
        displayDisk(nextDisk);
        if (primaryStage != null) {
            primaryStage.setTitle("AppleCommanderFX - " + nextDisk.getDiskName());
        }
    }

    @FXML
    private void closeDisk() {
        availableDisks.clear();
        currentDiskIndex = -1;
        currentDisk = null;
        currentDirectory = null;
        directoryPath.clear();
        currentContentMode = CONTENT_FILES;
        showDeletedFiles = false;
        fileTable.setItems(FXCollections.emptyObservableList());
        fileTable.getColumns().clear();
        setContentControlsEnabled(false);
        setViewControlsEnabled(false);
        if (filesContentButton != null) {
            filesContentButton.setSelected(true);
        }
        if (diskUsageContentButton != null) {
            diskUsageContentButton.setSelected(false);
        }
        setDeletedFilesButtonState();
        updateSwitchDiskButton();
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
    private void selectFilesContent() {
        applyContentMode(CONTENT_FILES);
    }

    @FXML
    private void selectDiskUsageContent() {
        applyContentMode(CONTENT_DISK_USAGE);
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

    @FXML
    private void toggleDeletedFiles() {
        showDeletedFiles = deletedFilesToggleButton != null && deletedFilesToggleButton.isSelected();
        setDeletedFilesButtonState();
        if (currentDisk != null && currentContentMode == CONTENT_FILES) {
            refreshDiskView();
        }
    }

    private void applyContentMode(int contentMode) {
        this.currentContentMode = contentMode;

        if (filesContentButton != null) {
            filesContentButton.setSelected(contentMode == CONTENT_FILES);
        }
        if (diskUsageContentButton != null) {
            diskUsageContentButton.setSelected(contentMode == CONTENT_DISK_USAGE);
        }

        boolean filesSelected = contentMode == CONTENT_FILES;
        setViewControlsEnabled(currentDisk != null && filesSelected);
        if (standardToolButton != null) {
            standardToolButton.setVisible(filesSelected);
            standardToolButton.setManaged(filesSelected);
        }
        if (nativeToolButton != null) {
            nativeToolButton.setVisible(filesSelected);
            nativeToolButton.setManaged(filesSelected);
        }
        if (detailToolButton != null) {
            detailToolButton.setVisible(filesSelected);
            detailToolButton.setManaged(filesSelected);
        }
        if (standardViewMenuItem != null) {
            standardViewMenuItem.setVisible(filesSelected);
        }
        if (nativeViewMenuItem != null) {
            nativeViewMenuItem.setVisible(filesSelected);
        }
        if (detailViewMenuItem != null) {
            detailViewMenuItem.setVisible(filesSelected);
        }
        if (deletedFilesToggleButton != null) {
            deletedFilesToggleButton.setVisible(filesSelected);
            deletedFilesToggleButton.setManaged(filesSelected);
            deletedFilesToggleButton.setDisable(currentDisk == null || !filesSelected);
        }
        if (breadcrumbBar != null) {
            boolean breadcrumbSupported = currentDisk instanceof ProdosFormatDisk;
            breadcrumbBar.setVisible(filesSelected && breadcrumbSupported);
            breadcrumbBar.setManaged(filesSelected && breadcrumbSupported);
        }

        // toggle which content pane is visible
        if (fileTable != null) {
            fileTable.setVisible(filesSelected);
            fileTable.setManaged(filesSelected);
        }
        if (diskUsagePane != null) {
            diskUsagePane.setVisible(!filesSelected);
            diskUsagePane.setManaged(!filesSelected);
        }

        if (currentDisk != null) {
            refreshDiskView();
        }
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
        if (standardViewMenuItem != null) {
            standardViewMenuItem.setSelected(displayMode == FormattedDisk.FILE_DISPLAY_STANDARD);
        }
        if (nativeViewMenuItem != null) {
            nativeViewMenuItem.setSelected(displayMode == FormattedDisk.FILE_DISPLAY_NATIVE);
        }
        if (detailViewMenuItem != null) {
            detailViewMenuItem.setSelected(displayMode == FormattedDisk.FILE_DISPLAY_DETAIL);
        }

        if (currentDisk != null) {
            refreshDiskView();
        }
    }

    private void setContentControlsEnabled(boolean enabled) {
        if (filesContentButton != null) {
            filesContentButton.setDisable(!enabled);
        }
        if (diskUsageContentButton != null) {
            diskUsageContentButton.setDisable(!enabled);
        }
        if (deletedFilesToggleButton != null) {
            deletedFilesToggleButton.setDisable(!enabled || currentContentMode != CONTENT_FILES);
        }
    }

    private void setViewControlsEnabled(boolean enabled) {
        if (standardToolButton != null) {
            standardToolButton.setDisable(!enabled);
        }
        if (nativeToolButton != null) {
            nativeToolButton.setDisable(!enabled);
        }
        if (detailToolButton != null) {
            detailToolButton.setDisable(!enabled);
        }
        if (standardViewMenuItem != null) {
            standardViewMenuItem.setDisable(!enabled);
        }
        if (nativeViewMenuItem != null) {
            nativeViewMenuItem.setDisable(!enabled);
        }
        if (detailViewMenuItem != null) {
            detailViewMenuItem.setDisable(!enabled);
        }
    }

    private void displayDisk(FormattedDisk disk) {
        currentDisk = disk;
        currentDiskIndex = availableDisks.indexOf(disk);
        currentDirectory = disk;
        directoryPath = new java.util.ArrayList<>();
        directoryPath.add(disk);
        setContentControlsEnabled(true);
        setDeletedFilesButtonState();
        updateSwitchDiskButton();

        // Enable disk-usage only if the disk reports support
        if (diskUsageContentButton != null) {
            boolean supported = disk.supportsDiskMap();
            diskUsageContentButton.setDisable(!supported);
            if (!supported && currentContentMode == CONTENT_DISK_USAGE) {
                // fall back to files view
                currentContentMode = CONTENT_FILES;
            }
        }

        setViewControlsEnabled(currentContentMode == CONTENT_FILES);
        refreshDiskView();
    }

    private void navigateToDirectory(DirectoryEntry directory) {
        if (directory == null) {
            return;
        }
        currentDirectory = directory;
        if (directoryPath.contains(directory)) {
            while (directoryPath.size() > 1 && !directoryPath.getLast().equals(directory)) {
                directoryPath.removeLast();
            }
        } else {
            directoryPath.add(directory);
        }
        refreshDiskView();
    }

    private void refreshDiskView() {
        if (currentDisk == null) {
            if (breadcrumbBar != null) {
                breadcrumbBar.setVisible(false);
                breadcrumbBar.setManaged(false);
            }
            return;
        }

        try {
            if (breadcrumbBar != null) {
                boolean breadcrumbSupported = currentContentMode == CONTENT_FILES && currentDisk instanceof ProdosFormatDisk;
                breadcrumbBar.setVisible(breadcrumbSupported);
                breadcrumbBar.setManaged(breadcrumbSupported);
            }
            updateSwitchDiskButton();
            refreshBreadcrumbs();
            if (currentContentMode == CONTENT_DISK_USAGE) {
                // Render the disk usage map
                renderDiskUsage(currentDisk);
                statusLabel.setText(buildDiskStatusText());
                return;
            }
            // Files view
            if (diskUsagePane != null) {
                diskUsagePane.setVisible(false);
                diskUsagePane.setManaged(false);
            }
            if (fileTable != null) {
                fileTable.setVisible(true);
                fileTable.setManaged(true);
            }
            populateDiskRows(currentDirectory, currentDisplayMode);
            statusLabel.setText(buildDiskStatusText());
        } catch (DiskException ex) {
            currentDisk = null;
            setContentControlsEnabled(false);
            setViewControlsEnabled(false);
            showErrorDialog("Could not read files from disk image", ex);
            fileTable.setItems(FXCollections.emptyObservableList());
            fileTable.getColumns().clear();
            statusLabel.setText("No disk image opened.");
        }
    }

    private void renderDiskUsage(FormattedDisk disk) throws DiskException {
        if (disk == null) return;
        if (diskUsagePane == null || diskUsageCanvas == null) return;

        if (!disk.supportsDiskMap()) {
            // nothing to render
            diskUsagePane.setVisible(false);
            diskUsagePane.setManaged(false);
            return;
        }

        // Show disk usage pane, hide file table
        diskUsagePane.setVisible(true);
        diskUsagePane.setManaged(true);
        fileTable.setVisible(false);
        fileTable.setManaged(false);

        String[] bitmapLabels = disk.getBitmapLabels();
        int[] dims = disk.getBitmapDimensions();

        int xCount = 1;
        int yCount = 1;
        String xLabel = "BLOCKS";
        String yLabel = "BLOCKS";
        int totalBlocks = -1;

        boolean isDim2 = (dims != null && dims.length >= 2);
        boolean isSingleDim = (dims != null && dims.length == 1) || (dims == null && disk.getBitmapLength() > 0);

        // Determine labels from bitmapLabels when available
        if (bitmapLabels != null && bitmapLabels.length > 0) {
            if (isDim2 && bitmapLabels.length >= 2) {
                xLabel = bitmapLabels[0];
                yLabel = bitmapLabels[1];
            } else {
                // single-dimension label applies to both axes
                xLabel = bitmapLabels[0];
                yLabel = bitmapLabels[0];
            }
        } else {
            if (isDim2) {
                xLabel = "TRACKS";
                yLabel = "SECTORS";
            } else {
                xLabel = "BLOCKS";
                yLabel = "BLOCKS";
            }
        }

        if (isDim2) {
            xCount = dims[0];
            yCount = dims[1];
        } else if (isSingleDim) {
            totalBlocks = dims != null ? dims[0] : disk.getBitmapLength();
            // Compute a near-square grid to render individual blocks
            int cols = (int) Math.ceil(Math.sqrt(totalBlocks));
            int rows = (int) Math.ceil((double) totalBlocks / cols);
            xCount = Math.max(1, cols);
            yCount = Math.max(1, rows);
        }

        // Draw onto canvas
        javafx.scene.canvas.GraphicsContext gc = diskUsageCanvas.getGraphicsContext2D();
        double w = diskUsageCanvas.getWidth();
        double h = diskUsageCanvas.getHeight();
        if (w <= 0) w = 800;
        if (h <= 0) h = 480;

        double padding = 8.0;
        double gap = 2.0;

        // Determine label font sizes and measure required label areas
        javafx.scene.text.Font titleFont = javafx.scene.text.Font.font(12);
        javafx.scene.text.Font labelFont = javafx.scene.text.Font.font(10);

        // Measure top labels (column numbers) and left labels (row numbers)
        double maxTopLabelWidth = 0.0;
        double maxTopLabelHeight = 0.0;
        for (int col = 0; col < xCount; col++) {
            String label = Integer.toString(col);
            javafx.scene.text.Text t = new javafx.scene.text.Text(label);
            t.setFont(labelFont);
            javafx.geometry.Bounds b = t.getLayoutBounds();
            maxTopLabelWidth = Math.max(maxTopLabelWidth, b.getWidth());
            maxTopLabelHeight = Math.max(maxTopLabelHeight, b.getHeight());
        }

        double maxLeftLabelWidth = 0.0;
        double maxLeftLabelHeight = 0.0;
        for (int row = 0; row < yCount; row++) {
            String label = Integer.toString(row);
            javafx.scene.text.Text t = new javafx.scene.text.Text(label);
            t.setFont(labelFont);
            javafx.geometry.Bounds b = t.getLayoutBounds();
            maxLeftLabelWidth = Math.max(maxLeftLabelWidth, b.getWidth());
            maxLeftLabelHeight = Math.max(maxLeftLabelHeight, b.getHeight());
        }

        // Split top area into title area and number area to prevent overlap
        double topTitleHeight = titleFont.getSize() + 4.0;
        double topNumberHeight = maxTopLabelHeight + 6.0;
        double topLabelHeight = Math.max(24.0, topTitleHeight + topNumberHeight + 4.0);

        // Split left area into title area (for rotated title) and number area
        double leftTitleWidth = titleFont.getSize() + 6.0; // rotated title approx
        double leftNumberWidth = maxLeftLabelWidth + 8.0;
        double leftLabelWidth = Math.max(48.0, leftTitleWidth + leftNumberWidth + 6.0);

        double availableW = Math.max(10, w - padding * 2 - leftLabelWidth);
        double availableH = Math.max(10, h - padding * 2 - topLabelHeight);

        double cellW = xCount > 0 ? (availableW - (xCount - 1) * gap) / xCount : availableW;
        double cellH = yCount > 0 ? (availableH - (yCount - 1) * gap) / yCount : availableH;
        gc.clearRect(0, 0, w, h);

        // Colors
        javafx.scene.paint.Color freeColor = javafx.scene.paint.Color.web("#90EE90"); // lightgreen
        javafx.scene.paint.Color usedColor = javafx.scene.paint.Color.web("#F08080"); // lightcoral
        javafx.scene.paint.Color borderColor = javafx.scene.paint.Color.web("#000000");
        javafx.scene.paint.Color textColor = OsThemeDetector.getDetector().isDark() ? javafx.scene.paint.Color.web("#E0E0E0") : javafx.scene.paint.Color.web("#000000");

        // Draw axis titles and numeric labels
        gc.setFill(textColor);
        // Use measured titleFont and labelFont from earlier
        gc.setFont(titleFont);

        // Draw top title (centered in title area)
        String topTitle = xLabel;
        double titleX = padding + leftLabelWidth + availableW / 2.0;
        double titleY = padding + topTitleHeight / 2.0;
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        gc.fillText(topTitle, titleX, titleY);

        // Draw top numeric labels (in their own band below the title)
        gc.setFont(labelFont);
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        double topNumbersY = padding + topTitleHeight + topNumberHeight / 2.0;
        for (int col = 0; col < xCount; col++) {
            // show 0, then every 5th index (0,5,10,...) and always show last index
            if ((col % 5 != 0) && col != xCount - 1) continue;
            String label;
            if (dims == null || dims.length == 1) {
                // single-dimension blocks: top label shows starting block index for column
                int labelVal = col * yCount;
                label = Integer.toString(labelVal);
            } else {
                label = Integer.toString(col);
            }
            double xCenter = padding + leftLabelWidth + col * (cellW + gap) + cellW / 2.0;
            gc.fillText(label, xCenter, topNumbersY);
        }

        // Left vertical title: place in the left title band, centered vertically against grid
        gc.save();
        String leftTitle = yLabel;
        double leftTitleCenterX = padding + leftTitleWidth / 2.0;
        double leftTitleCenterY = padding + topLabelHeight + availableH / 2.0;
        gc.translate(leftTitleCenterX, leftTitleCenterY);
        gc.rotate(-90);
        gc.fillText(leftTitle, 0, 0);
        gc.restore();

        // Left numeric labels: place in the left number band (to the right of the rotated title)
        double leftNumbersX = padding + leftTitleWidth + leftNumberWidth / 2.0;
        for (int row = 0; row < yCount; row++) {
            // show 0, then every 5th index (0,5,10,...) and always show last index
            if ((row % 5 != 0) && row != yCount - 1) continue;
            String label = Integer.toString(row);
            double yCenter = padding + topLabelHeight + row * (cellH + gap) + cellH / 2.0;
            gc.fillText(label, leftNumbersX, yCenter);
        }

        // Iterate DiskUsage - column-major (columns first)
        FormattedDisk.DiskUsage usage = disk.getDiskUsage();

        for (int col = 0; col < xCount; col++) {
            for (int row = 0; row < yCount; row++) {
                if (!usage.hasNext()) {
                    // no more data
                    break;
                }
                usage.next();
                boolean isFree = usage.isFree();
                boolean isUsed = usage.isUsed();

                double x = padding + leftLabelWidth + col * (cellW + gap);
                double y = padding + topLabelHeight + row * (cellH + gap);

                if (isFree) {
                    gc.setFill(freeColor);
                } else if (isUsed) {
                    gc.setFill(usedColor);
                } else {
                    gc.setFill(javafx.scene.paint.Color.LIGHTGRAY);
                }
                gc.fillRect(x, y, Math.max(1, cellW), Math.max(1, cellH));
                gc.setStroke(borderColor);
                gc.strokeRect(x, y, Math.max(1, cellW), Math.max(1, cellH));
            }
        }

        // Legend
        if (legendBox != null) {
            legendBox.getChildren().clear();

            javafx.scene.layout.HBox freeLegend = new javafx.scene.layout.HBox(6);
            javafx.scene.layout.Region freeSwatch = new javafx.scene.layout.Region();
            freeSwatch.setStyle("-fx-background-color: #90EE90; -fx-border-color: #000000; -fx-min-width: 16px; -fx-min-height: 16px;");
            Label freeLabel = new Label("Free");
            freeLegend.getChildren().addAll(freeSwatch, freeLabel);

            javafx.scene.layout.HBox usedLegend = new javafx.scene.layout.HBox(6);
            javafx.scene.layout.Region usedSwatch = new javafx.scene.layout.Region();
            usedSwatch.setStyle("-fx-background-color: #F08080; -fx-border-color: #000000; -fx-min-width: 16px; -fx-min-height: 16px;");
            Label usedLabel = new Label("Used");
            usedLegend.getChildren().addAll(usedSwatch, usedLabel);

            legendBox.getChildren().addAll(freeLegend, usedLegend);
        }
    }

    private void populateDiskRows(DirectoryEntry directory, int displayMode) throws DiskException {
        fileTable.getColumns().clear();
        List<FormattedDisk.FileColumnHeader> headers = currentDisk.getFileColumnHeaders(displayMode);

        for (int i = 0; i < headers.size(); i++) {
            final int columnIndex = i;
            FormattedDisk.FileColumnHeader header = headers.get(i);
            TableColumn<DiskFileRow, String> column = new TableColumn<>(header.getTitle());
            column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().values().get(columnIndex)));
            column.setMinWidth(60);
            column.setPrefWidth(Math.clamp(header.getMaximumWidth() * 7L, 80, 220));
            column.setMaxWidth(400);
            if (header.isRightAlign()) {
                column.setStyle("-fx-alignment: CENTER-RIGHT;");
            } else if (header.isCenterAlign()) {
                column.setStyle("-fx-alignment: CENTER;");
            }
            fileTable.getColumns().add(column);
        }

        List<DiskFileRow> rows = directory.getFiles().stream()
                .filter(fileEntry -> showDeletedFiles || !fileEntry.isDeleted())
                .map(fileEntry -> new DiskFileRow(fileEntry, fileEntry.getFileColumnData(displayMode)))
                .toList();

        javafx.collections.ObservableList<DiskFileRow> rowList = FXCollections.observableArrayList(rows);
        SortedList<DiskFileRow> sortedRows = new SortedList<>(rowList);
        sortedRows.comparatorProperty().bind(fileTable.comparatorProperty());
        fileTable.setItems(sortedRows);
        fileTable.getSortOrder().clear();
    }

    private void refreshBreadcrumbs() {
        if (breadcrumbBar == null) {
            return;
        }
        breadcrumbBar.getChildren().clear();
        if (!(currentDisk instanceof ProdosFormatDisk)) {
            return;
        }

        Label pathLabel = new Label("Path:");
        breadcrumbBar.getChildren().add(pathLabel);
        if (directoryPath == null || directoryPath.isEmpty()) {
            return;
        }

        String volumeName = currentDisk instanceof ProdosFormatDisk prodosDisk
                ? prodosDisk.getDiskName().replace("/", "")
                : "/";

        for (int i = 0; i < directoryPath.size(); i++) {
            DirectoryEntry dir = directoryPath.get(i);
            String crumbText = (dir == currentDisk) ? volumeName : dir.getDirname();
            Button crumb = new Button(crumbText);
            final int index = i;
            crumb.setOnAction(event -> {
                List<DirectoryEntry> newPath = new java.util.ArrayList<>(directoryPath.subList(0, index + 1));
                directoryPath.clear();
                directoryPath.addAll(newPath);
                currentDirectory = directoryPath.getLast();
                refreshDiskView();
            });
            Label separator = new Label("/");
            breadcrumbBar.getChildren().add(separator);
            breadcrumbBar.getChildren().add(crumb);
        }
    }

    private void setDeletedFilesButtonState() {
        if (deletedFilesToggleButton == null) {
            return;
        }

        deletedFilesToggleButton.setSelected(showDeletedFiles);
        if (deletedFilesIcon != null) {
            String imagePath = showDeletedFiles
                    ? "/org/applecommander/images/deleted-files-visible.png"
                    : "/org/applecommander/images/deleted-files-hidden.png";
            deletedFilesIcon.setImage(new javafx.scene.image.Image(getClass().getResource(imagePath).toExternalForm()));
        }
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

    private void updateSwitchDiskButton() {
        if (switchDiskButton == null) {
            return;
        }
        boolean enabled = availableDisks != null && availableDisks.size() > 1 && currentDisk != null;
        switchDiskButton.setDisable(!enabled);
        switchDiskButton.setVisible(enabled);
        switchDiskButton.setManaged(enabled);
    }

    private String buildDiskStatusText() {
        if (currentDisk == null) {
            return "No disk image opened.";
        }
        String diskName = currentDisk.getDiskName();
        String format = currentDisk.getFormat();
        if (availableDisks != null && availableDisks.size() > 1) {
            return "Current disk (" + (currentDiskIndex + 1) + " of " + availableDisks.size() + "): " + diskName + " (" + format + ")";
        }
        return "Current disk: " + diskName + " (" + format + ")";
    }

    private void showErrorDialog(String message, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Disk Browser Error");
        alert.setHeaderText(message);
        alert.setContentText(ex.getMessage() == null ? "An unexpected error occurred." : ex.getMessage());
        alert.showAndWait();
    }

    public record DiskFileRow(FileEntry fileEntry, List<String> values) {
    }
}
