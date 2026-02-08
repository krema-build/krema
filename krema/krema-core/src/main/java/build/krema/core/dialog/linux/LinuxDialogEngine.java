package build.krema.core.dialog.linux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import build.krema.core.dialog.DialogEngine;

/**
 * Linux DialogEngine implementation using zenity (GNOME) with kdialog fallback (KDE).
 */
public final class LinuxDialogEngine implements DialogEngine {

    private enum Backend { ZENITY, KDIALOG, NONE }

    private final Backend backend;

    public LinuxDialogEngine() {
        if (isCommandAvailable("zenity")) {
            backend = Backend.ZENITY;
        } else if (isCommandAvailable("kdialog")) {
            backend = Backend.KDIALOG;
        } else {
            backend = Backend.NONE;
        }
    }

    @Override
    public String openFile(FileDialogOptions options) {
        if (backend == Backend.NONE) return null;

        List<String> cmd = new ArrayList<>();
        if (backend == Backend.ZENITY) {
            cmd.add("zenity");
            cmd.add("--file-selection");
            if (options != null && options.title() != null) {
                cmd.add("--title=" + options.title());
            }
            if (options != null && options.defaultPath() != null) {
                cmd.add("--filename=" + options.defaultPath());
            }
            addZenityFilters(cmd, options);
        } else {
            cmd.add("kdialog");
            cmd.add("--getopenfilename");
            cmd.add(options != null && options.defaultPath() != null ? options.defaultPath() : "");
            addKdialogFilters(cmd, options);
            if (options != null && options.title() != null) {
                cmd.add("--title");
                cmd.add(options.title());
            }
        }

        return runAndGetOutput(cmd);
    }

    @Override
    public List<String> openFiles(FileDialogOptions options) {
        if (backend == Backend.NONE) return null;

        List<String> cmd = new ArrayList<>();
        if (backend == Backend.ZENITY) {
            cmd.add("zenity");
            cmd.add("--file-selection");
            cmd.add("--multiple");
            cmd.add("--separator=|");
            if (options != null && options.title() != null) {
                cmd.add("--title=" + options.title());
            }
            if (options != null && options.defaultPath() != null) {
                cmd.add("--filename=" + options.defaultPath());
            }
            addZenityFilters(cmd, options);
        } else {
            cmd.add("kdialog");
            cmd.add("--getopenfilename");
            cmd.add(options != null && options.defaultPath() != null ? options.defaultPath() : "");
            cmd.add("--multiple");
            addKdialogFilters(cmd, options);
            if (options != null && options.title() != null) {
                cmd.add("--title");
                cmd.add(options.title());
            }
        }

        String output = runAndGetOutput(cmd);
        if (output == null) return null;

        String separator = backend == Backend.ZENITY ? "\\|" : "\n";
        return Arrays.asList(output.split(separator));
    }

    @Override
    public String saveFile(FileDialogOptions options) {
        if (backend == Backend.NONE) return null;

        List<String> cmd = new ArrayList<>();
        if (backend == Backend.ZENITY) {
            cmd.add("zenity");
            cmd.add("--file-selection");
            cmd.add("--save");
            cmd.add("--confirm-overwrite");
            if (options != null && options.title() != null) {
                cmd.add("--title=" + options.title());
            }
            if (options != null && options.defaultPath() != null) {
                cmd.add("--filename=" + options.defaultPath());
            }
            addZenityFilters(cmd, options);
        } else {
            cmd.add("kdialog");
            cmd.add("--getsavefilename");
            cmd.add(options != null && options.defaultPath() != null ? options.defaultPath() : "");
            addKdialogFilters(cmd, options);
            if (options != null && options.title() != null) {
                cmd.add("--title");
                cmd.add(options.title());
            }
        }

        return runAndGetOutput(cmd);
    }

    @Override
    public String selectFolder(FolderDialogOptions options) {
        if (backend == Backend.NONE) return null;

        List<String> cmd = new ArrayList<>();
        if (backend == Backend.ZENITY) {
            cmd.add("zenity");
            cmd.add("--file-selection");
            cmd.add("--directory");
            if (options != null && options.title() != null) {
                cmd.add("--title=" + options.title());
            }
            if (options != null && options.defaultPath() != null) {
                cmd.add("--filename=" + options.defaultPath());
            }
        } else {
            cmd.add("kdialog");
            cmd.add("--getexistingdirectory");
            cmd.add(options != null && options.defaultPath() != null ? options.defaultPath() : "");
            if (options != null && options.title() != null) {
                cmd.add("--title");
                cmd.add(options.title());
            }
        }

        return runAndGetOutput(cmd);
    }

    @Override
    public void showMessage(String title, String message, MessageType type) {
        if (backend == Backend.NONE) return;

        List<String> cmd = new ArrayList<>();
        if (backend == Backend.ZENITY) {
            cmd.add("zenity");
            switch (type) {
                case WARNING -> cmd.add("--warning");
                case ERROR -> cmd.add("--error");
                default -> cmd.add("--info");
            }
            if (title != null) cmd.add("--title=" + title);
            cmd.add("--text=" + message);
        } else {
            cmd.add("kdialog");
            switch (type) {
                case WARNING -> cmd.add("--sorry");
                case ERROR -> cmd.add("--error");
                default -> cmd.add("--msgbox");
            }
            cmd.add(message);
            if (title != null) {
                cmd.add("--title");
                cmd.add(title);
            }
        }

        runAndWait(cmd);
    }

    @Override
    public boolean showConfirm(String title, String message) {
        if (backend == Backend.NONE) return false;

        List<String> cmd = new ArrayList<>();
        if (backend == Backend.ZENITY) {
            cmd.add("zenity");
            cmd.add("--question");
            if (title != null) cmd.add("--title=" + title);
            cmd.add("--text=" + message);
        } else {
            cmd.add("kdialog");
            cmd.add("--yesno");
            cmd.add(message);
            if (title != null) {
                cmd.add("--title");
                cmd.add(title);
            }
        }

        return runAndWait(cmd) == 0;
    }

    @Override
    public String showPrompt(String title, String message, String defaultValue) {
        if (backend == Backend.NONE) return null;

        List<String> cmd = new ArrayList<>();
        if (backend == Backend.ZENITY) {
            cmd.add("zenity");
            cmd.add("--entry");
            if (title != null) cmd.add("--title=" + title);
            cmd.add("--text=" + message);
            if (defaultValue != null) cmd.add("--entry-text=" + defaultValue);
        } else {
            cmd.add("kdialog");
            cmd.add("--inputbox");
            cmd.add(message);
            if (defaultValue != null) cmd.add(defaultValue);
            if (title != null) {
                cmd.add("--title");
                cmd.add(title);
            }
        }

        return runAndGetOutput(cmd);
    }

    private void addZenityFilters(List<String> cmd, FileDialogOptions options) {
        if (options == null || options.filters() == null) return;
        for (FileFilter filter : options.filters()) {
            String exts = filter.extensions().stream()
                .map(e -> "*." + e)
                .collect(Collectors.joining(" "));
            cmd.add("--file-filter=" + filter.name() + " | " + exts);
        }
    }

    private void addKdialogFilters(List<String> cmd, FileDialogOptions options) {
        if (options == null || options.filters() == null || options.filters().isEmpty()) return;
        String filter = options.filters().stream()
            .map(f -> {
                String exts = f.extensions().stream()
                    .map(e -> "*." + e)
                    .collect(Collectors.joining(" "));
                return f.name() + " (" + exts + ")";
            })
            .collect(Collectors.joining("\n"));
        // kdialog expects filter as an argument after the start directory
        cmd.add(filter);
    }

    private String runAndGetOutput(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n")).trim();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isEmpty()) {
                return null;
            }
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private int runAndWait(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process process = pb.start();
            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }
    }

    private static boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
