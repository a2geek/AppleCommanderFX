package org.applecommander.fx;

import com.jthemedetecor.OsThemeDetector;
import com.webcodepro.applecommander.storage.FileEntry;
import com.webcodepro.applecommander.storage.FileFilter;
import com.webcodepro.applecommander.storage.filters.ApplesoftFileFilter;
import com.webcodepro.applecommander.storage.filters.AppleWorksDataBaseFileFilter;
import com.webcodepro.applecommander.storage.filters.AppleWorksSpreadSheetFileFilter;
import com.webcodepro.applecommander.storage.filters.AppleWorksWordProcessorFileFilter;
import com.webcodepro.applecommander.storage.filters.AssemblySourceFileFilter;
import com.webcodepro.applecommander.storage.filters.BinaryFileFilter;
import com.webcodepro.applecommander.storage.filters.BusinessBASICFileFilter;
import com.webcodepro.applecommander.storage.filters.DisassemblyFileFilter;
import com.webcodepro.applecommander.storage.filters.GutenbergFileFilter;
import com.webcodepro.applecommander.storage.filters.GraphicsFileFilter;
import com.webcodepro.applecommander.storage.filters.HexDumpFileFilter;
import com.webcodepro.applecommander.storage.filters.IntegerBasicFileFilter;
import com.webcodepro.applecommander.storage.filters.MBASICFileFilter;
import com.webcodepro.applecommander.storage.filters.PascalCodeFileFilter;
import com.webcodepro.applecommander.storage.filters.PascalTextFileFilter;
import com.webcodepro.applecommander.storage.filters.ShapeTableFileFilter;
import com.webcodepro.applecommander.storage.filters.TextFileFilter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileViewer {
    private static final Map<Stage, Set<Stage>> OWNER_WINDOWS = new HashMap<>();
    private static final List<FilterPreset> FILTER_PRESETS = List.of(
            new FilterPreset("Applesoft", ApplesoftFileFilter.class),
            new FilterPreset("ADB", AppleWorksDataBaseFileFilter.class),
            new FilterPreset("ASP", AppleWorksSpreadSheetFileFilter.class),
            new FilterPreset("AWP", AppleWorksWordProcessorFileFilter.class),
            new FilterPreset("Assembly", AssemblySourceFileFilter.class),
            new FilterPreset("BASIC", BusinessBASICFileFilter.class),
            new FilterPreset("Disassembly", DisassemblyFileFilter.class),
            new FilterPreset("Gutenberg", GutenbergFileFilter.class),
            new FilterPreset("Hex Dump", HexDumpFileFilter.class),
            new FilterPreset("Integer", IntegerBasicFileFilter.class),
            new FilterPreset("MBASIC", MBASICFileFilter.class),
            new FilterPreset("CODE", PascalCodeFileFilter.class),
            new FilterPreset("TEXT", PascalTextFileFilter.class),
            new FilterPreset("Text", TextFileFilter.class)
    );

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
        textArea.setStyle("-fx-font-family: 'Courier New', Courier, monospace; -fx-font-size: 12px;");

        ToolBar toolbar = buildToolbar(stage, fileEntry, textArea);
        root.setTop(toolbar);

        textArea.setText(loadText(fileEntry, determineDefaultFilter(fileEntry)));

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
        toolbar.setPrefHeight(72);
        for (FilterPreset preset : FILTER_PRESETS) {
            Button button = createToolbarButton(preset.label());
            button.setOnAction(event -> textArea.setText(loadText(fileEntry, preset.filterClass())));
            toolbar.getItems().add(button);
        }

        return toolbar;
    }

    private static Button createToolbarButton(String label) {
        Button button = new Button();
        button.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        button.setGraphicTextGap(2);
        button.setMinWidth(90);
        button.setPrefHeight(60);

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

    private static Class<? extends FileFilter> determineDefaultFilter(FileEntry fileEntry) {
        FileFilter suggested = fileEntry.getSuggestedFilter();
        if (suggested == null) {
            return HexDumpFileFilter.class;
        }
        if (suggested instanceof GraphicsFileFilter || suggested instanceof ShapeTableFileFilter || suggested instanceof BinaryFileFilter) {
            return HexDumpFileFilter.class;
        }
        for (FilterPreset preset : FILTER_PRESETS) {
            if (preset.filterClass().isInstance(suggested)) {
                return preset.filterClass();
            }
        }
        return HexDumpFileFilter.class;
    }

    private static String loadText(FileEntry fileEntry, Class<? extends FileFilter> filterClass) {
        FileFilter filter = instantiateFilter(filterClass, fileEntry);
        if (filter == null) {
            filter = new HexDumpFileFilter();
        }

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

    private record FilterPreset(String label, Class<? extends FileFilter> filterClass) {
    }
}
