package build.krema.core.api.dragdrop;

import java.util.List;
import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.dragdrop.DragDropOptions;

/**
 * Drag-drop configuration commands.
 * The actual drag-drop handling is done via JavaScript (krema-dragdrop.js).
 * Events are emitted through the standard event system.
 */
public class DragDrop {

    @KremaCommand("dragdrop:configure")
    public void configure(Map<String, Object> options) {
        // Configuration is handled by the JavaScript handler
        // This command triggers the JS configuration via eval
        // The actual call is forwarded to window.__krema_dragdrop.configure()
    }

    @KremaCommand("dragdrop:enable")
    public void enable() {
        // Handled by JavaScript
    }

    @KremaCommand("dragdrop:disable")
    public void disable() {
        // Handled by JavaScript
    }

    /**
     * Parses options map into DragDropOptions record.
     */
    public static DragDropOptions parseOptions(Map<String, Object> options) {
        if (options == null) {
            return DragDropOptions.defaults();
        }

        DragDropOptions.Builder builder = DragDropOptions.builder();

        if (options.containsKey("enabled")) {
            builder.enabled((Boolean) options.get("enabled"));
        }

        if (options.containsKey("acceptedExtensions")) {
            @SuppressWarnings("unchecked")
            List<String> extensions = (List<String>) options.get("acceptedExtensions");
            builder.acceptedExtensions(extensions);
        }

        if (options.containsKey("acceptedMimeTypes")) {
            @SuppressWarnings("unchecked")
            List<String> mimeTypes = (List<String>) options.get("acceptedMimeTypes");
            builder.acceptedMimeTypes(mimeTypes);
        }

        if (options.containsKey("dropZoneSelector")) {
            builder.dropZoneSelector((String) options.get("dropZoneSelector"));
        }

        return builder.build();
    }
}
