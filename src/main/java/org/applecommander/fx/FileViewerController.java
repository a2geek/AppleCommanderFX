package org.applecommander.fx;

import com.webcodepro.applecommander.storage.FileEntry;
import com.webcodepro.applecommander.storage.FileFilter;
import com.webcodepro.applecommander.storage.filters.*;
import com.webcodepro.applecommander.storage.os.dos33.DosFormatDisk;
import com.webcodepro.applecommander.util.AppleUtil;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import javafx.animation.PauseTransition;
import javafx.stage.Popup;
import javafx.util.Duration;
import javafx.stage.Window;

public class FileViewerController {
    @FXML
    private ToolBar toolbar;
    @FXML
    private ScrollPane textScrollPane;
    @FXML
    private ScrollPane imageScrollPane;
    @FXML
    private TextArea textArea;
    @FXML
    private ImageView imageView;
    @FXML
    private Label sizeLabel;

    private final ToggleGroup toggleGroup = new ToggleGroup();
    private FileEntry fileEntry;

    private int fontPointSize = 12;
    private final int minFontPointSize = 8;
    private int imageScale = 1; // x1, x2, ...

    public void init(FileEntry fileEntry) {
        this.fileEntry = fileEntry;
        configureTextArea();
        configureImageView();
        // initialize sizes from defaults in UI or current textArea font
        fontPointSize = (int) Math.max(minFontPointSize, Math.round(textArea.getFont().getSize()));
        imageScale = 1;
        buildToolbar();
        updateSizeLabel();
    }

    public void bindScene(Scene scene) {
        if (scene == null) return;
        // Increase: Shortcut + PLUS or EQUALS
        scene.getAccelerators().put(new KeyCharacterCombination("+", KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_ANY), this::onIncrease);
        scene.getAccelerators().put(new KeyCharacterCombination("=", KeyCombination.SHORTCUT_DOWN), this::onIncrease);

        // Decrease: Shortcut + MINUS
        scene.getAccelerators().put(new KeyCharacterCombination("-", KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_ANY), this::onDecrease);

        // Reset: Shortcut + DIGIT0
        scene.getAccelerators().put(new KeyCharacterCombination("0", KeyCombination.SHORTCUT_DOWN), this::onReset);

        // Copy: Shortcut + C (prefer platform standard). Register both lowercase and uppercase
        scene.getAccelerators().put(new KeyCharacterCombination("c", KeyCombination.SHORTCUT_DOWN), this::onCopy);
        scene.getAccelerators().put(new KeyCharacterCombination("C", KeyCombination.SHORTCUT_DOWN), this::onCopy);
    }

    private void onReset() {
        if (textScrollPane.isVisible()) {
            fontPointSize = Math.max(minFontPointSize, 12);
            textArea.setFont(Font.font(textArea.getFont().getFamily(), fontPointSize));
        } else if (imageScrollPane.isVisible()) {
            imageScale = 1;
            imageView.setScaleX(imageScale);
            imageView.setScaleY(imageScale);
        }
        updateSizeLabel();
    }

    private void configureTextArea() {
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setFont(Font.font("Monospaced", 12));
    }

    private void configureImageView() {
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
    }

    private void buildToolbar() {
        toolbar.getItems().clear();

        final FileFilter suggestedFileFilter = switch (fileEntry.getSuggestedFilter()) {
            case BinaryFileFilter _ -> new HexDumpFileFilter();
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
        initialToggleButton.setOnAction(e -> applyFilter(suggestedFileFilter));
        hBox.getChildren().add(initialToggleButton);

        // Add hex/disassembly alternate
        if (suggestedFileFilter instanceof HexDumpFileFilter) {
            FileFilter disasm = new DisassemblyFileFilter(fileEntry);
            ToggleButton tb = createToggleButton(getFileFilterLabel(disasm), disasm);
            tb.setToggleGroup(toggleGroup);
            tb.setOnAction(e -> applyFilter(disasm));
            hBox.getChildren().add(tb);
        } else {
            FileFilter hex = new HexDumpFileFilter();
            ToggleButton tb = createToggleButton(getFileFilterLabel(hex), hex);
            tb.setToggleGroup(toggleGroup);
            tb.setOnAction(e -> applyFilter(hex));
            hBox.getChildren().add(tb);
        }

        if (fileEntry.getFormattedDisk() instanceof DosFormatDisk dosFormatDisk) {
            ToggleButton tb = createToggleButton("Raw Data", null);
            tb.setToggleGroup(toggleGroup);
            tb.setOnAction(e -> applyRaw(dosFormatDisk));
            hBox.getChildren().add(tb);
        }

        // Add spacer and size controls
        toolbar.getItems().add(new Separator());
        Button decrease = createIconButton("/org/applecommander/images/minus.png", "Decrease size");
        Button increase = createIconButton("/org/applecommander/images/plus.png", "Increase size");
        decrease.setOnAction(e -> onDecrease());
        increase.setOnAction(e -> onIncrease());
        toolbar.getItems().addAll(decrease, increase);

        // Copy button separated by a divider
        toolbar.getItems().add(new Separator());
        Button copyBtn = createIconButton("/org/applecommander/images/copy.png", "Copy");
        copyBtn.setText("Copy");
        copyBtn.setOnAction(e -> onCopy());
        toolbar.getItems().add(copyBtn);

        // Ensure the first button is visibly selected and content is loaded.
        initialToggleButton.setSelected(true);
        toggleGroup.selectToggle(initialToggleButton);
        applyFilter(suggestedFileFilter);

        // Ensure toolbar buttons have enough height
        toolbar.setStyle("-fx-padding:8; -fx-background-insets: 0; -fx-background-radius: 0;");
    }

    private ToggleButton createToggleButton(String label, FileFilter filter) {
        ToggleButton button = new ToggleButton();
        button.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        button.setGraphicTextGap(2);
        button.setStyle("-fx-padding: 4 8 4 8; -fx-background-radius: 0; -fx-border-radius: 0; -fx-background-insets: 0;");
        button.setText(label.split("\\s")[0]);
        button.setTooltip(new Tooltip(String.format("View as %s", label)));
        button.setMinWidth(90);

        Image icon = new Image(FileViewerController.class.getResource("/org/applecommander/images/file.png").toExternalForm());
        ImageView iconView = new ImageView(icon);
        iconView.setFitWidth(24);
        iconView.setFitHeight(24);
        iconView.setPreserveRatio(true);
        button.setGraphic(iconView);

        // store filter in userData for later invocation
        button.setUserData(filter);

        return button;
    }

    private Button createIconButton(String imagePath, String tooltip) {
        Button btn = new Button();
        btn.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        btn.setMinWidth(90);
        try {
            Image icon = new Image(FileViewerController.class.getResource(imagePath).toExternalForm());
            ImageView iv = new ImageView(icon);
            iv.setFitWidth(24);
            iv.setFitHeight(24);
            iv.setPreserveRatio(true);
            btn.setGraphic(iv);
        } catch (Exception e) {
            // fall through; label will be set below
        }
        // label the buttons for clarity
        if (tooltip != null && tooltip.toLowerCase().contains("decrease")) {
            btn.setText("Decrease");
        } else if (tooltip != null && tooltip.toLowerCase().contains("increase")) {
            btn.setText("Increase");
        }
        btn.setTooltip(new Tooltip(tooltip));
        return btn;
    }

    private void onDecrease() {
        if (textScrollPane.isVisible()) {
            adjustFont(-1);
        } else if (imageScrollPane.isVisible()) {
            adjustImageScale(-1);
        }
    }

    private void onIncrease() {
        if (textScrollPane.isVisible()) {
            adjustFont(+1);
        } else if (imageScrollPane.isVisible()) {
            adjustImageScale(+1);
        }
    }

    private void onCopy() {
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            boolean didCopy = false;
            if (textScrollPane.isVisible()) {
                String text = textArea.getText();
                if (text == null) text = "";
                if (!text.isEmpty()) {
                    content.putString(text);
                    didCopy = true;
                }
            } else if (imageScrollPane.isVisible()) {
                Image img = imageView.getImage();
                if (img != null) {
                    content.putImage(img);
                    didCopy = true;
                }
            }
            if (didCopy) {
                clipboard.setContent(content);
                showCopyNotification("Copied to clipboard");
            } else {
                showCopyNotification("Nothing to copy");
            }
        } catch (Exception e) {
            // best effort; show text error in UI
            textArea.setText("Copy failed: " + e.getMessage());
            showCopyNotification("Copy failed");
        }
    }

    private void adjustFont(int delta) {
        int newSize = Math.max(minFontPointSize, fontPointSize + delta);
        if (newSize != fontPointSize) {
            fontPointSize = newSize;
            textArea.setFont(Font.font(textArea.getFont().getFamily(), fontPointSize));
            updateSizeLabel();
        }
    }

    private void adjustImageScale(int delta) {
        int newScale = Math.max(1, imageScale + delta);
        if (newScale != imageScale) {
            imageScale = newScale;
            imageView.setScaleX(imageScale);
            imageView.setScaleY(imageScale);
            updateSizeLabel();
        }
    }

    private void updateSizeLabel() {
        if (sizeLabel == null) return;
        if (textScrollPane.isVisible()) {
            String family = textArea.getFont().getFamily();
            sizeLabel.setText(String.format("Size: %s %dpt", family, fontPointSize));
        } else if (imageScrollPane.isVisible()) {
            sizeLabel.setText(String.format("Size: x%d", imageScale));
        } else {
            sizeLabel.setText("");
        }
    }

    private void applyFilter(FileFilter filter) {
        if (filter == null) {
            return;
        }
        try {
            byte[] data = filter.filter(fileEntry);
            if (data == null) data = new byte[0];

            boolean isImage = false;
            Image img = null;
            try {
                // Try to create an Image from the bytes
                img = new Image(new ByteArrayInputStream(data));
                if (!img.isError() && img.getWidth() > 0) {
                    isImage = true;
                }
            } catch (Exception ex) {
                isImage = false;
            }

            if (isImage) {
                imageView.setImage(img);
                // apply current image scale
                imageView.setScaleX(imageScale);
                imageView.setScaleY(imageScale);

                imageScrollPane.setVisible(true);
                imageScrollPane.setManaged(true);
                // ensure scroll pane doesn't try to fit image to viewport
                imageScrollPane.setFitToWidth(false);
                imageScrollPane.setFitToHeight(false);
                imageScrollPane.setPannable(true);

                textScrollPane.setVisible(false);
                textScrollPane.setManaged(false);
            } else {
                String text = new String(data, StandardCharsets.ISO_8859_1);
                text = text.replace("\u0000", "");
                textArea.setText(text);
                textScrollPane.setVisible(true);
                textScrollPane.setManaged(true);
                imageScrollPane.setVisible(false);
                imageScrollPane.setManaged(false);
            }
            // update label after switching
            updateSizeLabel();
        } catch (Exception e) {
            textArea.setText("Error applying filter: " + e.getMessage());
            textScrollPane.setVisible(true);
            textScrollPane.setManaged(true);
            imageScrollPane.setVisible(false);
            imageScrollPane.setManaged(false);
        }
    }

    private void applyRaw(DosFormatDisk dosFormatDisk) {
        try {
            byte[] data = dosFormatDisk.getFileData(fileEntry);
            textArea.setText(AppleUtil.getHexDump(data));
        } catch (Exception e) {
            textArea.setText("Error loading raw data: " + e.getMessage());
        }
        textScrollPane.setVisible(true);
        textScrollPane.setManaged(true);
        imageScrollPane.setVisible(false);
        imageScrollPane.setManaged(false);
        updateSizeLabel();
    }

    private void showCopyNotification(String message) {
        try {
            Scene scene = toolbar.getScene();
            if (scene == null) return;
            Window window = scene.getWindow();
            if (window == null) return;

            Popup popup = new Popup();
            Label lbl = new Label(message);
            lbl.setStyle("-fx-background-color: rgba(0,0,0,0.75); -fx-text-fill: white; -fx-padding: 8; -fx-background-radius: 6;");
            popup.getContent().add(lbl);
            // show centered above the window bottom
            double x = window.getX() + (window.getWidth() - lbl.prefWidth(-1)) / 2;
            double y = window.getY() + window.getHeight() - 80;
            popup.show(window, x, y);

            PauseTransition pt = new PauseTransition(Duration.seconds(1.5));
            pt.setOnFinished(ev -> popup.hide());
            pt.play();
        } catch (Exception ignored) {
        }
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
