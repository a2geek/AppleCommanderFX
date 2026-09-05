package org.applecommander.fx;

import com.jthemedetecor.OsThemeDetector;
import com.webcodepro.applecommander.storage.FileEntry;
import com.webcodepro.applecommander.storage.FileFilter;
import com.webcodepro.applecommander.storage.filters.*;
import com.webcodepro.applecommander.storage.os.dos33.DosFormatDisk;
import com.webcodepro.applecommander.util.AppleUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
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

        BorderPane root = new BorderPane();
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setFont(Font.font("Monospaced", 12));

        ToolBar toolbar = buildToolbar(stage, fileEntry, textArea);
        root.setTop(toolbar);

        ScrollPane scrollPane = new ScrollPane(textArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        root.setCenter(scrollPane);
        root.setPadding(new Insets(4));

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

    private static ToolBar buildToolbar(Stage stage, FileEntry fileEntry, TextArea textArea) {
        ToolBar toolbar = new ToolBar();

        final FileFilter suggestedFileFilter = switch (fileEntry.getSuggestedFilter()) {
            case ShapeTableFileFilter _, GraphicsFileFilter _, BinaryFileFilter _ -> new HexDumpFileFilter();
            default -> {
                FileFilter fileFilter = fileEntry.getSuggestedFilter();
                if (fileFilter == null) {
                    yield new HexDumpFileFilter();
                }
                yield fileFilter;
            }
        };

        Button button = createToolbarButton(getFileFilterLabel(suggestedFileFilter));
        button.setOnAction(event -> textArea.setText(loadText(fileEntry, suggestedFileFilter)));
        toolbar.getItems().add(button);
        button.fire();  // Preloads text pane

        if (suggestedFileFilter instanceof HexDumpFileFilter) {
            FileFilter fileFilter = new DisassemblyFileFilter(fileEntry);
            button = createToolbarButton(getFileFilterLabel(fileFilter));
            button.setOnAction(event -> textArea.setText(loadText(fileEntry, fileFilter)));
            toolbar.getItems().add(button);
        }
        else {
            FileFilter fileFilter = new HexDumpFileFilter();
            button = createToolbarButton(getFileFilterLabel(fileFilter));
            button.setOnAction(event -> textArea.setText(loadText(fileEntry, fileFilter)));
            toolbar.getItems().add(button);
        }
        if (fileEntry.getFormattedDisk() instanceof DosFormatDisk dosFormatDisk) {
            button = createToolbarButton("Raw Data");
            button.setOnAction(event -> textArea.setText(AppleUtil.getHexDump(dosFormatDisk.getFileData(fileEntry))));
            toolbar.getItems().add(button);
        }

        return toolbar;
    }

    private static String getFileFilterLabel(FileFilter fileFilter) {
        return switch (fileFilter) {
            case ApplesoftFileFilter _ -> "Applesoft BASIC";
            case AppleWorksDataBaseFileFilter _ -> "ADB";
            case AppleWorksSpreadSheetFileFilter _ -> "ASP";
            case AppleWorksWordProcessorFileFilter _ -> "AWP";
            case AssemblySourceFileFilter _ -> "Assembly";
            case BinaryFileFilter _ -> "Binary";
            case DisassemblyFileFilter _ -> "Disassembly";
            case GraphicsFileFilter _ -> "Graphics";
            case GutenbergFileFilter _ -> "Gutenberg";
            case HexDumpFileFilter _ -> "Hex";
            case IntegerBasicFileFilter _ -> "Integer BASIC";
            case MBASICFileFilter _ -> "MBASIC";
            case PascalCodeFileFilter _ -> "CODE";
            case PascalTextFileFilter _ -> "TEXT";
            case ShapeTableFileFilter _ -> "Shape Table";
            case TextFileFilter _ -> "Text";
            default -> "Unknown - FIXME";
        };
    }

    private static Button createToolbarButton(String label) {
        Button button = new Button();
        button.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        button.setGraphicTextGap(2);
        button.setMinWidth(90);

        VBox graphicBox = new VBox(2);
        graphicBox.setAlignment(Pos.CENTER);
        Image icon = new Image(FileViewer.class.getResource("/org/applecommander/images/file.png").toExternalForm());
        ImageView iconView = new ImageView(icon);
        iconView.setFitWidth(16);
        iconView.setFitHeight(16);
        iconView.setPreserveRatio(true);
        Label nameLabel = new Label(label);
        graphicBox.getChildren().addAll(iconView, nameLabel);
        button.setGraphic(graphicBox);
        return button;
    }

    private static String loadText(FileEntry fileEntry, FileFilter filter) {
        byte[] data = filter.filter(fileEntry);
        String text = new String(data, StandardCharsets.ISO_8859_1);
        return text.replace("\u0000", "");
    }

    private static FileFilter instantiateFilter(Class<? extends FileFilter> filterClass, FileEntry fileEntry) {
        try {
            if (filterClass == DisassemblyFileFilter.class) {
                return new DisassemblyFileFilter(fileEntry);
            }
            return filterClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            return null;
        }
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
