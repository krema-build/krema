package build.krema.cli.bundle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Interface for creating platform-specific application bundles.
 *
 * Following Tauri's model, bundling is a CLI concern - the app code
 * runs inside bundles but never creates them. This enables native
 * OS features like notifications that require proper bundle identity.
 */
public interface AppBundler {

    /**
     * Creates a minimal dev bundle for development mode.
     * The dev bundle provides just enough structure for native OS features
     * to work (bundle identifier, Info.plist, etc.) while keeping the
     * classpath and dependencies external for fast iteration.
     *
     * Dev bundles are cached in .krema/dev-bundle/ and only recreated
     * when configuration changes.
     *
     * @param config Bundle configuration
     * @return Path to the bundle executable/launcher
     * @throws IOException if bundle creation fails
     */
    Path createDevBundle(AppBundleConfig config) throws IOException;

    /**
     * Creates a production bundle for distribution.
     * The production bundle is self-contained with all JARs, native libraries,
     * and resources embedded.
     *
     * @param config Bundle configuration
     * @return Path to the bundle directory
     * @throws IOException if bundle creation fails
     */
    Path createProductionBundle(AppBundleConfig config) throws IOException;

    /**
     * Launches the application through the bundle.
     * Environment variables are passed to the launcher script/executable.
     *
     * @param bundlePath Path to the bundle (e.g., .app directory on macOS)
     * @param env Environment variables to pass to the app
     * @return The launched process
     * @throws IOException if launch fails
     */
    Process launch(Path bundlePath, Map<String, String> env) throws IOException;

    /**
     * Returns true if this platform requires bundling for native features.
     * On some platforms (like macOS), native APIs like notifications require
     * the app to run as a proper bundle with identifier.
     *
     * @return true if bundling is required for full native feature support
     */
    boolean requiresBundleForNativeFeatures();
}
