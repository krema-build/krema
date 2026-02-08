package build.krema.plugin.positioner;

import java.util.List;

import build.krema.core.KremaCommand;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.screen.ScreenBounds;
import build.krema.core.screen.ScreenEngineFactory;
import build.krema.core.screen.ScreenInfo;
import build.krema.core.window.WindowEngine;
import build.krema.core.window.WindowEngineFactory;

/**
 * Built-in window positioner plugin.
 * Provides semantic window positioning using screen-aware coordinates.
 */
public class PositionerPlugin implements KremaPlugin {

    @Override
    public String getId() {
        return "krema.positioner";
    }

    @Override
    public String getName() {
        return "Positioner";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Semantic window positioning with screen-aware coordinates";
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new PositionerCommands());
    }

    @Override
    public List<String> getRequiredPermissions() {
        return List.of("window:manage");
    }

    public enum Position {
        TOP_LEFT, TOP_RIGHT, TOP_CENTER,
        BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER,
        CENTER, LEFT_CENTER, RIGHT_CENTER
    }

    public record MoveToRequest(String position) {}

    public static class PositionerCommands {

        @KremaCommand("positioner:moveTo")
        public boolean moveTo(MoveToRequest request) {
            String normalized = request.position().toUpperCase().replace("-", "_");
            Position position = Position.valueOf(normalized);

            WindowEngine windowEngine = WindowEngineFactory.get();
            ScreenInfo screen = ScreenEngineFactory.get().getPrimaryScreen();
            ScreenBounds usable = screen.visibleFrame();

            int[] windowSize = windowEngine.getSize();
            int winW = windowSize[0];
            int winH = windowSize[1];

            int x = switch (position) {
                case TOP_LEFT, BOTTOM_LEFT, LEFT_CENTER -> (int) usable.x();
                case TOP_RIGHT, BOTTOM_RIGHT, RIGHT_CENTER -> (int) (usable.x() + usable.width() - winW);
                case TOP_CENTER, BOTTOM_CENTER, CENTER -> (int) (usable.x() + (usable.width() - winW) / 2);
            };

            int y = switch (position) {
                case TOP_LEFT, TOP_RIGHT, TOP_CENTER -> (int) (usable.y() + usable.height() - winH);
                case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER -> (int) usable.y();
                case LEFT_CENTER, RIGHT_CENTER, CENTER -> (int) (usable.y() + (usable.height() - winH) / 2);
            };

            windowEngine.setPosition(x, y);
            return true;
        }
    }
}
