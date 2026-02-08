package build.krema.core.plugin;

/**
 * Exception thrown when plugin operations fail.
 */
public class PluginException extends Exception {

    public PluginException(String message) {
        super(message);
    }

    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }
}
