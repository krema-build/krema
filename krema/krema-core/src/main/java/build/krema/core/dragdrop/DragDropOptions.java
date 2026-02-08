package build.krema.core.dragdrop;

import java.util.List;

/**
 * Configuration options for drag-drop functionality.
 */
public record DragDropOptions(
    boolean enabled,
    List<String> acceptedExtensions,
    List<String> acceptedMimeTypes,
    String dropZoneSelector
) {

    /**
     * Default options with drag-drop enabled for all files on the entire window.
     */
    public static DragDropOptions defaults() {
        return new DragDropOptions(true, null, null, null);
    }

    /**
     * Builder for DragDropOptions.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enabled = true;
        private List<String> acceptedExtensions;
        private List<String> acceptedMimeTypes;
        private String dropZoneSelector;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder acceptedExtensions(List<String> extensions) {
            this.acceptedExtensions = extensions;
            return this;
        }

        public Builder acceptedMimeTypes(List<String> mimeTypes) {
            this.acceptedMimeTypes = mimeTypes;
            return this;
        }

        public Builder dropZoneSelector(String selector) {
            this.dropZoneSelector = selector;
            return this;
        }

        public DragDropOptions build() {
            return new DragDropOptions(enabled, acceptedExtensions, acceptedMimeTypes, dropZoneSelector);
        }
    }
}
