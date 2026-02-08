package build.krema.core.updater.install;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Platform-specific update installer.
 * Handles extracting/installing the update file and restarting the application.
 */
public interface UpdateInstaller {

    /**
     * Installs the update from the given file.
     *
     * @param updateFile path to the downloaded update artifact
     * @throws IOException if installation fails
     */
    void install(Path updateFile) throws IOException;

    /**
     * Restarts the application after an update has been installed.
     *
     * @throws IOException if restart fails
     */
    void restart() throws IOException;
}
