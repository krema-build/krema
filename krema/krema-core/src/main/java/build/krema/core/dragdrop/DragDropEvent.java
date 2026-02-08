package build.krema.core.dragdrop;

import java.util.List;

/**
 * Event payload for drag-drop operations.
 */
public record DragDropEvent(
    String type,
    List<DroppedFile> files,
    double x,
    double y
) {

    /**
     * Information about a dropped file.
     */
    public record DroppedFile(
        String name,
        String path,
        String type,
        long size
    ) {}
}
