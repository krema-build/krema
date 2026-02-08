package build.krema.core.api.screen;

import java.util.List;
import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.screen.*;

/**
 * Screen information commands using platform-specific implementations.
 * On macOS: Uses NSScreen and NSEvent via FFM.
 */
public class Screen {

    private final ScreenEngine engine = ScreenEngineFactory.get();

    @KremaCommand("screen:getAll")
    public List<ScreenInfo> getAll() {
        return engine.getAllScreens();
    }

    @KremaCommand("screen:getPrimary")
    public ScreenInfo getPrimary() {
        return engine.getPrimaryScreen();
    }

    @KremaCommand("screen:getCount")
    public int getCount() {
        return engine.getAllScreens().size();
    }

    @KremaCommand("screen:getCursorPosition")
    public CursorPosition getCursorPosition() {
        return engine.getCursorPosition();
    }

    @KremaCommand("screen:getScreenAtPoint")
    public ScreenInfo getScreenAtPoint(Map<String, Object> options) {
        double x = ((Number) options.get("x")).doubleValue();
        double y = ((Number) options.get("y")).doubleValue();
        return engine.getScreenAtPoint(x, y);
    }

    @KremaCommand("screen:getScreenAtCursor")
    public ScreenInfo getScreenAtCursor() {
        CursorPosition pos = engine.getCursorPosition();
        return engine.getScreenAtPoint(pos.x(), pos.y());
    }
}
