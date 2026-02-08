package build.krema.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.concurrent.Callable;

import build.krema.cli.init.InitConfig;
import build.krema.cli.init.InteractivePrompter;
import build.krema.cli.init.TemplateGenerator;

/**
 * Creates a new Krema project with interactive prompts.
 *
 * Supports multiple modes:
 * - Quick mode (default): prompts for name + template
 * - Guided mode (--guided): 5 key questions
 * - Wizard mode (--wizard): full configuration
 * - CI mode (--ci or CI=true): no prompts, all defaults
 */
@Command(
    name = "init",
    description = "Create a new Krema project"
)
public class InitCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Project name", defaultValue = "")
    private String projectName;

    @Option(names = {"-t", "--template"}, description = "Template: vanilla, react, vue, svelte, angular")
    private String template;

    @Option(names = {"-f", "--force"}, description = "Overwrite existing files")
    private boolean force;

    @Option(names = {"--guided"}, description = "Guided setup with key options (name, template, title, identifier, TypeScript)")
    private boolean guided;

    @Option(names = {"--wizard"}, description = "Full configuration wizard")
    private boolean wizard;

    @Option(names = {"--ci"}, description = "Non-interactive mode (uses defaults)")
    private boolean ci;

    @Option(names = {"--title"}, description = "Window title")
    private String windowTitle;

    @Option(names = {"--identifier"}, description = "Bundle identifier (e.g., com.example.myapp)")
    private String identifier;

    @Option(names = {"--typescript"}, negatable = true, defaultValue = "true", description = "Use TypeScript (default: true)")
    private boolean typescript;

    @Option(names = {"--description"}, description = "App description")
    private String description;

    @Option(names = {"--package-manager"}, description = "Package manager: npm, pnpm, yarn")
    private String packageManager;

    @Override
    public Integer call() {
        try {
            InitConfig config = buildConfig();
            new TemplateGenerator(config, force).generate();
            return 0;
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private InitConfig buildConfig() throws IOException {
        InitConfig config;

        if (isNonInteractiveMode()) {
            config = buildConfigFromFlags();
        } else if (wizard) {
            config = runWizardMode();
        } else if (guided) {
            config = runGuidedMode();
        } else {
            config = runQuickMode();
        }

        applyFlagOverrides(config);
        return config;
    }

    private boolean isNonInteractiveMode() {
        return ci || "true".equalsIgnoreCase(System.getenv("CI"));
    }

    private InitConfig buildConfigFromFlags() {
        InitConfig config = new InitConfig();

        // App name
        String name = projectName;
        if (name == null || name.isEmpty() || ".".equals(name)) {
            name = "krema-app";
        }
        config.setAppName(validateAppName(name));

        // Template
        config.setTemplate(template != null ? template : "vanilla");

        // Guided options
        config.setTypescript(typescript);

        return config;
    }

    private InitConfig runQuickMode() throws IOException {
        try (var prompter = createPrompter()) {
            return prompter.runQuickMode(projectName);
        }
    }

    private InitConfig runGuidedMode() throws IOException {
        try (var prompter = createPrompter()) {
            return prompter.runGuidedMode(projectName);
        }
    }

    private InitConfig runWizardMode() throws IOException {
        try (var prompter = createPrompter()) {
            return prompter.runWizardMode();
        }
    }

    private CloseablePrompter createPrompter() throws IOException {
        return new CloseablePrompter(new InteractivePrompter());
    }

    private void applyFlagOverrides(InitConfig config) {
        // Template can always be overridden
        if (template != null) {
            config.setTemplate(template);
        }

        // Guided/wizard options
        if (windowTitle != null) {
            config.setWindowTitle(windowTitle);
        }
        if (identifier != null) {
            config.setIdentifier(identifier);
        }
        if (description != null) {
            config.setDescription(description);
        }
        if (packageManager != null) {
            config.setPackageManager(packageManager);
        }

        // TypeScript option (handle negatable)
        config.setTypescript(typescript);
    }

    private String validateAppName(String name) {
        if (name == null || name.isBlank() || !name.matches("[a-zA-Z][a-zA-Z0-9-]*")) {
            System.out.println("  Warning: Invalid app name '" + name + "', using 'krema-app' instead");
            return "krema-app";
        }
        return name;
    }

    /**
     * Wrapper to make InteractivePrompter usable with try-with-resources.
     */
    private static class CloseablePrompter implements AutoCloseable {
        private final InteractivePrompter prompter;

        CloseablePrompter(InteractivePrompter prompter) {
            this.prompter = prompter;
        }

        InitConfig runQuickMode(String name) {
            return prompter.runQuickMode(name);
        }

        InitConfig runGuidedMode(String name) {
            return prompter.runGuidedMode(name);
        }

        InitConfig runWizardMode() {
            return prompter.runWizardMode();
        }

        @Override
        public void close() {
            prompter.close();
        }
    }
}
