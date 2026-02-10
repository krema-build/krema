package build.krema.cli.init;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

import java.io.IOException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Interactive terminal prompts using JLine3.
 */
public class InteractivePrompter {

    private enum SelectOp { UP, DOWN, ACCEPT, IGNORE }

    private static final List<String> TEMPLATES = List.of("vanilla", "react", "vue", "svelte", "angular");
    private static final List<String> PACKAGE_MANAGERS = List.of("npm", "pnpm", "yarn");

    private final Terminal terminal;
    private final LineReader reader;

    public InteractivePrompter() throws IOException {
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }

    /**
     * Prompt for text input with an optional default value.
     */
    public String promptText(String prompt, String defaultValue) {
        String displayPrompt = formatPrompt(prompt, defaultValue);
        String input = reader.readLine(displayPrompt).trim();
        return input.isEmpty() ? defaultValue : input;
    }

    /**
     * Prompt for selecting from a list of options using arrow-key navigation.
     */
    public String promptSelect(String prompt, List<String> options, String defaultValue) {
        terminal.writer().println();
        terminal.writer().println(formatQuestion(prompt) + "  (↑/↓ to move, Enter to select)");

        int selected = defaultValue != null ? options.indexOf(defaultValue) : 0;
        if (selected < 0) selected = 0;

        renderSelectOptions(options, selected);
        terminal.writer().flush();

        Attributes originalAttributes = terminal.getAttributes();
        Attributes rawAttributes = new Attributes(originalAttributes);
        rawAttributes.setLocalFlag(Attributes.LocalFlag.ICANON, false);
        rawAttributes.setLocalFlag(Attributes.LocalFlag.ECHO, false);
        rawAttributes.setInputFlag(Attributes.InputFlag.ICRNL, false);
        terminal.setAttributes(rawAttributes);

        try {
            KeyMap<SelectOp> keyMap = new KeyMap<>();
            keyMap.bind(SelectOp.UP, KeyMap.key(terminal, Capability.key_up));
            keyMap.bind(SelectOp.DOWN, KeyMap.key(terminal, Capability.key_down));
            keyMap.bind(SelectOp.ACCEPT, "\r", "\n");
            keyMap.setNomatch(SelectOp.IGNORE);

            BindingReader bindingReader = new BindingReader(terminal.reader());
            while (true) {
                SelectOp op = bindingReader.readBinding(keyMap);
                if (op == null) {
                    break;
                }

                switch (op) {
                    case UP:
                        selected = (selected - 1 + options.size()) % options.size();
                        break;
                    case DOWN:
                        selected = (selected + 1) % options.size();
                        break;
                    case ACCEPT:
                        break;
                    case IGNORE:
                        continue;
                }

                if (op == SelectOp.ACCEPT) {
                    break;
                }

                // Move cursor up to re-render options
                terminal.writer().print("\033[" + options.size() + "A");
                renderSelectOptions(options, selected);
                terminal.writer().flush();
            }
        } finally {
            terminal.setAttributes(originalAttributes);
        }

        // Print confirmed selection
        terminal.writer().println();
        terminal.writer().println("  Selected: " + new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append(options.get(selected))
                .toAnsi());

        return options.get(selected);
    }

    /**
     * Render select options with cursor highlight on the selected item.
     */
    private void renderSelectOptions(List<String> options, int selectedIndex) {
        for (int i = 0; i < options.size(); i++) {
            terminal.writer().print("\033[2K");
            if (i == selectedIndex) {
                terminal.writer().println("  " + new AttributedStringBuilder()
                        .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                        .append("❯ " + options.get(i))
                        .toAnsi());
            } else {
                terminal.writer().println("    " + options.get(i));
            }
        }
    }

    /**
     * Prompt for yes/no confirmation.
     */
    public boolean promptYesNo(String prompt, boolean defaultValue) {
        String hint = defaultValue ? "Y/n" : "y/N";
        String displayPrompt = formatPrompt(prompt + " (" + hint + ")", null);
        String input = reader.readLine(displayPrompt).trim().toLowerCase();

        if (input.isEmpty()) {
            return defaultValue;
        }
        return input.startsWith("y");
    }

    /**
     * Prompt for multi-select from plugins.
     * User enters comma-separated numbers or names to toggle selections.
     */
    public List<InitConfig.Plugin> promptPlugins(String prompt, List<InitConfig.Plugin> allPlugins) {
        terminal.writer().println();
        terminal.writer().println(formatQuestion(prompt));
        terminal.writer().println("  (Enter numbers to toggle, 'done' to confirm, 'all' to select all)");
        terminal.writer().println();

        // Track selected plugins
        Set<InitConfig.Plugin> selected = new HashSet<>();
        for (InitConfig.Plugin p : allPlugins) {
            if (p.isDefaultEnabled()) {
                selected.add(p);
            }
        }

        while (true) {
            // Display current state
            for (int i = 0; i < allPlugins.size(); i++) {
                InitConfig.Plugin plugin = allPlugins.get(i);
                String checkbox = selected.contains(plugin) ? "[x]" : "[ ]";
                String suffix = plugin.isBuiltIn() ? " (built-in)" : "";
                terminal.writer().printf("  %d. %s %s%s - %s%n",
                        i + 1, checkbox, plugin.getDisplayName(), suffix, plugin.getDescription());
            }
            terminal.writer().println();

            String input = reader.readLine("  Toggle (1,2,3...), 'done', or 'all': ").trim().toLowerCase();

            if (input.equals("done") || input.isEmpty()) {
                break;
            }

            if (input.equals("all")) {
                selected.addAll(allPlugins);
                terminal.writer().println("  Selected all plugins.");
                terminal.writer().println();
                continue;
            }

            if (input.equals("none")) {
                selected.clear();
                terminal.writer().println("  Cleared all selections.");
                terminal.writer().println();
                continue;
            }

            // Parse comma-separated numbers
            String[] parts = input.split("[,\\s]+");
            for (String part : parts) {
                try {
                    int index = Integer.parseInt(part.trim()) - 1;
                    if (index >= 0 && index < allPlugins.size()) {
                        InitConfig.Plugin plugin = allPlugins.get(index);
                        if (selected.contains(plugin)) {
                            selected.remove(plugin);
                            terminal.writer().println("  Removed: " + plugin.getDisplayName());
                        } else {
                            selected.add(plugin);
                            terminal.writer().println("  Added: " + plugin.getDisplayName());
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Try matching by name
                    for (InitConfig.Plugin plugin : allPlugins) {
                        if (plugin.getDisplayName().toLowerCase().contains(part) ||
                            plugin.getId().toLowerCase().contains(part)) {
                            if (selected.contains(plugin)) {
                                selected.remove(plugin);
                                terminal.writer().println("  Removed: " + plugin.getDisplayName());
                            } else {
                                selected.add(plugin);
                                terminal.writer().println("  Added: " + plugin.getDisplayName());
                            }
                            break;
                        }
                    }
                }
            }
            terminal.writer().println();
        }

        // Return in original order
        return allPlugins.stream()
                .filter(selected::contains)
                .collect(Collectors.toList());
    }

    /**
     * Quick mode: prompts for app name and template only.
     */
    public InitConfig runQuickMode(String providedName) {
        InitConfig config = new InitConfig();

        printBanner("Create a new Krema project");

        // App name
        if (providedName == null || providedName.isEmpty() || ".".equals(providedName)) {
            String name = promptText("App name", "my-app");
            config.setAppName(validateAppName(name));
        } else {
            config.setAppName(validateAppName(providedName));
        }

        // Template
        String template = promptSelect("Select template", TEMPLATES, "vanilla");
        config.setTemplate(template);

        return config;
    }

    /**
     * Guided mode: 5 key questions.
     */
    public InitConfig runGuidedMode(String providedName) {
        InitConfig config = new InitConfig();

        printBanner("Guided project setup");

        // App name
        if (providedName == null || providedName.isEmpty() || ".".equals(providedName)) {
            String name = promptText("App name", "my-app");
            config.setAppName(validateAppName(name));
        } else {
            config.setAppName(validateAppName(providedName));
        }

        // Template
        String template = promptSelect("Select template", TEMPLATES, "vanilla");
        config.setTemplate(template);

        // Window title
        String defaultTitle = toTitleCase(config.getAppName());
        String title = promptText("Window title", defaultTitle);
        config.setWindowTitle(title);

        // Bundle identifier
        String defaultId = "com.example." + config.getAppName().toLowerCase().replaceAll("[^a-z0-9]", "");
        String identifier = promptText("Bundle identifier", defaultId);
        config.setIdentifier(identifier);

        // TypeScript
        boolean typescript = promptYesNo("Use TypeScript?", true);
        config.setTypescript(typescript);

        return config;
    }

    /**
     * Wizard mode: full configuration.
     */
    public InitConfig runWizardMode() {
        InitConfig config = new InitConfig();

        printBanner("Full project wizard");

        // === Project Setup ===
        printSection("Project Setup");

        String name = promptText("App name", "my-app");
        config.setAppName(validateAppName(name));

        String description = promptText("Description", "A desktop application built with Krema");
        config.setDescription(description);

        String defaultId = "com.example." + config.getAppName().toLowerCase().replaceAll("[^a-z0-9]", "");
        String identifier = promptText("Bundle identifier", defaultId);
        config.setIdentifier(identifier);

        // === Frontend ===
        printSection("Frontend");

        String template = promptSelect("Select template", TEMPLATES, "vanilla");
        config.setTemplate(template);

        boolean typescript = promptYesNo("Use TypeScript?", true);
        config.setTypescript(typescript);

        String packageManager = promptSelect("Package manager", PACKAGE_MANAGERS, "npm");
        config.setPackageManager(packageManager);

        // === Window ===
        printSection("Window");

        String defaultTitle = toTitleCase(config.getAppName());
        String title = promptText("Window title", defaultTitle);
        config.setWindowTitle(title);

        String size = promptText("Window size (WxH)", "1024x768");
        parseWindowSize(size, config);

        boolean resizable = promptYesNo("Resizable window?", true);
        config.setResizable(resizable);

        // === Plugins ===
        printSection("Plugins");

        List<InitConfig.Plugin> allPlugins = Arrays.asList(InitConfig.Plugin.values());
        List<InitConfig.Plugin> selectedPlugins = promptPlugins("Select plugins to include", allPlugins);
        config.setPlugins(selectedPlugins);

        // === Features ===
        printSection("Features");

        String deepLink = promptText("Deep link scheme (leave empty to skip)", "");
        if (!deepLink.isEmpty()) {
            config.setDeepLinkScheme(deepLink);
        }

        // Summary
        printSummary(config);

        return config;
    }

    private void printBanner(String title) {
        terminal.writer().println();
        terminal.writer().println(new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN))
                .append("  " + title)
                .toAnsi());
        terminal.writer().println();
    }

    private void printSection(String title) {
        terminal.writer().println();
        terminal.writer().println(new AttributedStringBuilder()
                .style(AttributedStyle.BOLD)
                .append("=== " + title + " ===")
                .toAnsi());
    }

    private void printSummary(InitConfig config) {
        terminal.writer().println();
        terminal.writer().println(new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.GREEN))
                .append("Summary:")
                .toAnsi());
        terminal.writer().println("  App: " + config.getAppName());
        terminal.writer().println("  Template: " + config.getTemplate() + (config.isTypescript() ? " + TypeScript" : ""));
        terminal.writer().println("  Window: " + config.getWindowWidth() + "x" + config.getWindowHeight());
        terminal.writer().println("  Package manager: " + config.getPackageManager());

        if (!config.getPlugins().isEmpty()) {
            String pluginNames = config.getPlugins().stream()
                    .map(InitConfig.Plugin::getDisplayName)
                    .collect(Collectors.joining(", "));
            terminal.writer().println("  Plugins: " + pluginNames);
        }

        terminal.writer().println();
    }

    private String formatPrompt(String prompt, String defaultValue) {
        if (defaultValue != null && !defaultValue.isEmpty()) {
            return "? " + prompt + " [" + defaultValue + "]: ";
        }
        return "? " + prompt + ": ";
    }

    private String formatQuestion(String question) {
        return new AttributedStringBuilder()
                .style(AttributedStyle.BOLD)
                .append("? " + question)
                .toAnsi();
    }

    private String validateAppName(String name) {
        if (name == null || name.isBlank() || !name.matches("[a-zA-Z][a-zA-Z0-9-]*")) {
            terminal.writer().println("  Warning: Invalid app name '" + name + "', using 'krema-app' instead");
            return "krema-app";
        }
        return name;
    }

    private void parseWindowSize(String size, InitConfig config) {
        try {
            String[] parts = size.toLowerCase().split("x");
            if (parts.length == 2) {
                config.setWindowWidth(Integer.parseInt(parts[0].trim()));
                config.setWindowHeight(Integer.parseInt(parts[1].trim()));
            }
        } catch (NumberFormatException e) {
            // Keep defaults
        }
    }

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                result.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            } else {
                if (result.length() > 0) {
                    result.append(' ');
                }
                capitalizeNext = true;
            }
        }
        return result.toString();
    }

    public void close() {
        try {
            terminal.close();
        } catch (IOException ignored) {
        }
    }
}
