package build.krema.core.dialog;

import build.krema.core.ports.DialogPort;

/**
 * Platform-agnostic interface for native dialogs.
 * Implementations provide platform-specific dialog functionality.
 *
 * @deprecated Use {@link DialogPort} instead. This interface is maintained
 *             for backward compatibility and will be removed in a future version.
 * @see DialogPort
 */
@Deprecated(since = "2.0", forRemoval = true)
public interface DialogEngine extends DialogPort {
}
