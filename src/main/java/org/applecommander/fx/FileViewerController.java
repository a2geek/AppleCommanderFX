package org.applecommander.fx;

import com.webcodepro.applecommander.storage.FileEntry;
import com.webcodepro.applecommander.storage.FileFilter;
import com.webcodepro.applecommander.storage.filters.*;
import com.webcodepro.applecommander.storage.os.dos33.DosFormatDisk;
import com.webcodepro.applecommander.util.AppleUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

public class FileViewerController {
    @FXML
    private ToolBar toolbar;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private TextArea textArea;

    private final ToggleGroup toggleGroup = new ToggleGroup();
    private FileEntry fileEntry;

    public void init(FileEntry fileEntry) {
        this.fileEntry = fileEntry;
        configureTextArea();
        buildToolbar();
    }

    private void configureTextArea() {
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setFont(Font.font("Monospaced", 12));
    }

    private void buildToolbar() {
        toolbar.getItems().clear();

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

        HBox hBox = new HBox();
        hBox.setSpacing(0);
        toolbar.getItems().add(hBox);

        // Suggested filter button
        ToggleButton initialToggleButton = createToggleButton(getFileFilterLabel(suggestedFileFilter), suggestedFileFilter);
        initialToggleButton.setToggleGroup(toggleGroup);
        initialToggleButton.setOnAction(e -> textArea.setText(loadText(fileEntry, suggestedFileFilter)));
        hBox.getChildren().add(initialToggleButton);

        // Add hex/disassembly alternate
        if (suggestedFileFilter instanceof HexDumpFileFilter) {
            FileFilter disasm = new DisassemblyFileFilter(fileEntry);
            ToggleButton tb = createToggleButton(getFileFilterLabel(disasm), disasm);
            tb.setToggleGroup(toggleGroup);
            tb.setOnAction(e -> textArea.setText(loadText(fileEntry, disasm)));
            hBox.getChildren().add(tb);
        } else {
            FileFilter hex = new HexDumpFileFilter();
            ToggleButton tb = createToggleButton(getFileFilterLabel(hex), hex);
            tb.setToggleGroup(toggleGroup);
            tb.setOnAction(e -> textArea.setText(loadText(fileEntry, hex)));
            hBox.getChildren().add(tb);
        }

        if (fileEntry.getFormattedDisk() instanceof DosFormatDisk dosFormatDisk) {
            ToggleButton tb = createToggleButton("Raw Data", null);
            tb.setToggleGroup(toggleGroup);
            tb.setOnAction(e -> textArea.setText(AppleUtil.getHexDump(dosFormatDisk.getFileData(fileEntry))));
            hBox.getChildren().add(tb);
        }

        // Ensure the first button is visibly selected and content is loaded.
        initialToggleButton.setSelected(true);
        toggleGroup.selectToggle(initialToggleButton);
        textArea.setText(loadText(fileEntry, suggestedFileFilter));

        // Ensure toolbar buttons have enough height
        toolbar.setStyle("-fx-padding:8; -fx-background-insets: 0; -fx-background-radius: 0;");
    }

    private ToggleButton createToggleButton(String label, FileFilter filter) {
        ToggleButton button = new ToggleButton();
        button.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        button.setGraphicTextGap(2);
        button.setMinWidth(90);
        button.setMinHeight(64);

        VBox graphicBox = new VBox(2);
        graphicBox.setStyle("-fx-alignment:center;");
        Image icon = new Image(FileViewerController.class.getResource("/org/applecommander/images/file.png").toExternalForm());
        ImageView iconView = new ImageView(icon);
        iconView.setFitWidth(16);
        iconView.setFitHeight(16);
        iconView.setPreserveRatio(true);
        Label nameLabel = new Label(label);
        graphicBox.getChildren().addAll(iconView, nameLabel);
        button.setGraphic(graphicBox);

        // If filter is provided, wire the action to apply its output when selected
        if (filter != null) {
            button.setOnAction(e -> {
                if (button.isSelected()) {
                    textArea.setText(loadText(fileEntry, filter));
                }
            });
        }
        return button;
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

    private static String loadText(FileEntry fileEntry, FileFilter filter) {
        byte[] data = filter.filter(fileEntry);
        String text = new String(data, StandardCharsets.ISO_8859_1);
        return text.replace("\u0000", "");
    }

    // Exposed for tests or future use - instantiate with reflection if needed
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
}
