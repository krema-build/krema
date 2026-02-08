package build.krema.core.ports;

import java.util.List;

/**
 * Port interface for native dialogs.
 * Implementations provide platform-specific dialog functionality.
 *
 * @see build.krema.core.dialog.DialogEngine
 */
public interface DialogPort {

    /**
     * Shows a native file open dialog.
     *
     * @param options dialog options
     * @return selected file path, or null if cancelled
     */
    String openFile(FileDialogOptions options);

    /**
     * Shows a native file open dialog with multiple selection.
     *
     * @param options dialog options
     * @return list of selected file paths, or null if cancelled
     */
    List<String> openFiles(FileDialogOptions options);

    /**
     * Shows a native file save dialog.
     *
     * @param options dialog options
     * @return selected file path, or null if cancelled
     */
    String saveFile(FileDialogOptions options);

    /**
     * Shows a native folder selection dialog.
     *
     * @param options dialog options
     * @return selected folder path, or null if cancelled
     */
    String selectFolder(FolderDialogOptions options);

    /**
     * Shows a native message dialog.
     *
     * @param title dialog title
     * @param message message to display
     * @param type message type (info, warning, error)
     */
    void showMessage(String title, String message, MessageType type);

    /**
     * Shows a native confirmation dialog.
     *
     * @param title dialog title
     * @param message message to display
     * @return true if user confirmed, false otherwise
     */
    boolean showConfirm(String title, String message);

    /**
     * Shows a native text input prompt.
     *
     * @param title dialog title
     * @param message message to display
     * @param defaultValue default input value
     * @return user input, or null if cancelled
     */
    String showPrompt(String title, String message, String defaultValue);

    /**
     * Message dialog types.
     */
    enum MessageType {
        INFO,
        WARNING,
        ERROR
    }

    /**
     * File filter for dialog.
     */
    record FileFilter(String name, List<String> extensions) {}

    /**
     * Options for file dialogs.
     */
    record FileDialogOptions(
        String title,
        String defaultPath,
        List<FileFilter> filters
    ) {
        public FileDialogOptions() {
            this(null, null, null);
        }
    }

    /**
     * Options for folder dialogs.
     */
    record FolderDialogOptions(
        String title,
        String defaultPath
    ) {
        public FolderDialogOptions() {
            this(null, null);
        }
    }
}
