package build.krema.core.dialog.windows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import build.krema.core.dialog.DialogEngine;

/**
 * Windows DialogEngine implementation using PowerShell with WinForms.
 * Provides native file/folder dialogs and message boxes via subprocess.
 */
public final class WindowsDialogEngine implements DialogEngine {

    @Override
    public String openFile(FileDialogOptions options) {
        String filterStr = buildFilterString(options.filters());
        String script = buildOpenFileScript(filterStr, options.title(), options.defaultPath(), false);
        String result = execPowerShell(script);
        return (result != null && !result.isBlank()) ? result.strip() : null;
    }

    @Override
    public List<String> openFiles(FileDialogOptions options) {
        String filterStr = buildFilterString(options.filters());
        String script = buildOpenFileScript(filterStr, options.title(), options.defaultPath(), true);
        String result = execPowerShell(script);
        if (result == null || result.isBlank()) return null;
        List<String> files = new ArrayList<>();
        for (String line : result.strip().split("\\r?\\n")) {
            if (!line.isBlank()) files.add(line.strip());
        }
        return files.isEmpty() ? null : files;
    }

    @Override
    public String saveFile(FileDialogOptions options) {
        String filterStr = buildFilterString(options.filters());
        StringBuilder sb = new StringBuilder();
        sb.append("Add-Type -AssemblyName System.Windows.Forms\n");
        sb.append("$d = New-Object System.Windows.Forms.SaveFileDialog\n");
        if (options.title() != null) {
            sb.append("$d.Title = '").append(escapePS(options.title())).append("'\n");
        }
        if (filterStr != null) {
            sb.append("$d.Filter = '").append(escapePS(filterStr)).append("'\n");
        }
        if (options.defaultPath() != null) {
            sb.append("$d.InitialDirectory = '").append(escapePS(options.defaultPath())).append("'\n");
        }
        sb.append("if ($d.ShowDialog() -eq 'OK') { Write-Output $d.FileName }");
        String result = execPowerShell(sb.toString());
        return (result != null && !result.isBlank()) ? result.strip() : null;
    }

    @Override
    public String selectFolder(FolderDialogOptions options) {
        StringBuilder sb = new StringBuilder();
        sb.append("Add-Type -AssemblyName System.Windows.Forms\n");
        sb.append("$d = New-Object System.Windows.Forms.FolderBrowserDialog\n");
        if (options.title() != null) {
            sb.append("$d.Description = '").append(escapePS(options.title())).append("'\n");
        }
        if (options.defaultPath() != null) {
            sb.append("$d.SelectedPath = '").append(escapePS(options.defaultPath())).append("'\n");
        }
        sb.append("if ($d.ShowDialog() -eq 'OK') { Write-Output $d.SelectedPath }");
        String result = execPowerShell(sb.toString());
        return (result != null && !result.isBlank()) ? result.strip() : null;
    }

    @Override
    public void showMessage(String title, String message, MessageType type) {
        String icon = switch (type) {
            case INFO -> "Information";
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
        String script = String.format(
            "Add-Type -AssemblyName System.Windows.Forms; " +
            "[System.Windows.Forms.MessageBox]::Show('%s', '%s', 'OK', '%s')",
            escapePS(message), escapePS(title), icon
        );
        execPowerShell(script);
    }

    @Override
    public boolean showConfirm(String title, String message) {
        String script = String.format(
            "Add-Type -AssemblyName System.Windows.Forms; " +
            "$r = [System.Windows.Forms.MessageBox]::Show('%s', '%s', 'YesNo', 'Question'); " +
            "Write-Output $r",
            escapePS(message), escapePS(title)
        );
        String result = execPowerShell(script);
        return result != null && result.strip().equalsIgnoreCase("Yes");
    }

    @Override
    public String showPrompt(String title, String message, String defaultValue) {
        String defVal = defaultValue != null ? defaultValue : "";
        // Use a small VB-style InputBox via PowerShell
        String script = String.format(
            "$r = [Microsoft.VisualBasic.Interaction]::InputBox('%s', '%s', '%s'); " +
            "if ($r -ne '') { Write-Output $r }",
            escapePS(message), escapePS(title), escapePS(defVal)
        );
        // Need to load Microsoft.VisualBasic assembly
        String fullScript = "Add-Type -AssemblyName Microsoft.VisualBasic\n" + script;
        String result = execPowerShell(fullScript);
        return (result != null && !result.isBlank()) ? result.strip() : null;
    }

    private String buildOpenFileScript(String filter, String title, String defaultPath, boolean multiSelect) {
        StringBuilder sb = new StringBuilder();
        sb.append("Add-Type -AssemblyName System.Windows.Forms\n");
        sb.append("$d = New-Object System.Windows.Forms.OpenFileDialog\n");
        if (title != null) {
            sb.append("$d.Title = '").append(escapePS(title)).append("'\n");
        }
        if (filter != null) {
            sb.append("$d.Filter = '").append(escapePS(filter)).append("'\n");
        }
        if (defaultPath != null) {
            sb.append("$d.InitialDirectory = '").append(escapePS(defaultPath)).append("'\n");
        }
        if (multiSelect) {
            sb.append("$d.Multiselect = $true\n");
            sb.append("if ($d.ShowDialog() -eq 'OK') { $d.FileNames | ForEach-Object { Write-Output $_ } }");
        } else {
            sb.append("if ($d.ShowDialog() -eq 'OK') { Write-Output $d.FileName }");
        }
        return sb.toString();
    }

    private String buildFilterString(List<FileFilter> filters) {
        if (filters == null || filters.isEmpty()) return null;
        return filters.stream()
            .map(f -> {
                String exts = f.extensions().stream()
                    .map(e -> "*." + e)
                    .collect(Collectors.joining(";"));
                return f.name() + "|" + exts;
            })
            .collect(Collectors.joining("|"));
    }

    private static String escapePS(String s) {
        if (s == null) return "";
        return s.replace("'", "''");
    }

    private static String execPowerShell(String script) {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true)
                .start();
            String output;
            try (var is = p.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exitCode = p.waitFor();
            return exitCode == 0 ? output : null;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }
}
