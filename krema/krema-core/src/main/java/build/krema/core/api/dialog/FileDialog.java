package build.krema.core.api.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.dialog.DialogEngine;
import build.krema.core.dialog.DialogEngineFactory;
import build.krema.core.ports.DialogPort.FileDialogOptions;
import build.krema.core.ports.DialogPort.FileFilter;
import build.krema.core.ports.DialogPort.FolderDialogOptions;
import build.krema.core.ports.DialogPort.MessageType;

/**
 * Native dialog commands using platform-specific implementations.
 * On macOS: Uses Cocoa NSOpenPanel, NSSavePanel, NSAlert via FFM.
 */
public class FileDialog {

    private final DialogEngine engine = DialogEngineFactory.get();

    @KremaCommand("dialog:openFile")
    public String openFile(Map<String, Object> options) {
        return engine.openFile(parseFileOptions(options));
    }

    @KremaCommand("dialog:openFiles")
    public List<String> openFiles(Map<String, Object> options) {
        return engine.openFiles(parseFileOptions(options));
    }

    @KremaCommand("dialog:saveFile")
    public String saveFile(Map<String, Object> options) {
        return engine.saveFile(parseFileOptions(options));
    }

    @KremaCommand("dialog:selectFolder")
    public String selectFolder(Map<String, Object> options) {
        return engine.selectFolder(parseFolderOptions(options));
    }

    @KremaCommand("dialog:message")
    public void showMessage(String title, String message, String type) {
        MessageType messageType = switch (type != null ? type.toLowerCase() : "info") {
            case "error" -> MessageType.ERROR;
            case "warning" -> MessageType.WARNING;
            default -> MessageType.INFO;
        };
        engine.showMessage(title, message, messageType);
    }

    @KremaCommand("dialog:confirm")
    public boolean showConfirm(String title, String message) {
        return engine.showConfirm(title, message);
    }

    @KremaCommand("dialog:prompt")
    public String showPrompt(String title, String message, String defaultValue) {
        return engine.showPrompt(title, message, defaultValue);
    }

    private FileDialogOptions parseFileOptions(Map<String, Object> options) {
        if (options == null) {
            return new FileDialogOptions();
        }

        String title = options.containsKey("title") ? options.get("title").toString() : null;
        String defaultPath = options.containsKey("defaultPath") ? options.get("defaultPath").toString() : null;
        List<FileFilter> filters = null;

        if (options.containsKey("filters")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawFilters = (List<Map<String, Object>>) options.get("filters");
            filters = new ArrayList<>();
            for (Map<String, Object> filter : rawFilters) {
                String name = filter.get("name").toString();
                @SuppressWarnings("unchecked")
                List<String> extensions = (List<String>) filter.get("extensions");
                filters.add(new FileFilter(name, extensions));
            }
        }

        return new FileDialogOptions(title, defaultPath, filters);
    }

    private FolderDialogOptions parseFolderOptions(Map<String, Object> options) {
        if (options == null) {
            return new FolderDialogOptions();
        }

        String title = options.containsKey("title") ? options.get("title").toString() : null;
        String defaultPath = options.containsKey("defaultPath") ? options.get("defaultPath").toString() : null;

        return new FolderDialogOptions(title, defaultPath);
    }
}
