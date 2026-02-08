package build.krema.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Main CLI entry point for the Krema framework.
 */
@Command(
    name = "krema",
    mixinStandardHelpOptions = true,
    version = "Krema 0.1.0",
    description = "Lightweight desktop apps with system webviews",
    subcommands = {
        InitCommand.class,
        DevCommand.class,
        BuildCommand.class,
        BundleCommand.class,
        SignerCommand.class
    }
)
public class KremaCli implements Callable<Integer> {

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        CommandLine cmd = new CommandLine(new KremaCli())
            .setCaseInsensitiveEnumValuesAllowed(true);
        System.out.println("[Krema CLI] Initialized in " + (System.currentTimeMillis() - startTime) + "ms");

        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // Show help when no subcommand is provided
        CommandLine.usage(this, System.out);
        return 0;
    }
}
