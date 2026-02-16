package build.krema.core.window;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WindowOptions")
class WindowOptionsTest {

    @Test
    @DisplayName("defaults() returns sensible defaults")
    void defaultsReturnsSensibleValues() {
        WindowOptions opts = WindowOptions.defaults();

        assertEquals("Krema App", opts.title());
        assertEquals(1024, opts.width());
        assertEquals(768, opts.height());
        assertEquals(0, opts.minWidth());
        assertEquals(0, opts.minHeight());
        assertEquals(Integer.MAX_VALUE, opts.maxWidth());
        assertEquals(Integer.MAX_VALUE, opts.maxHeight());
        assertTrue(opts.resizable());
        assertFalse(opts.fullscreen());
        assertFalse(opts.alwaysOnTop());
        assertFalse(opts.transparent());
        assertTrue(opts.decorations());
        assertTrue(opts.center());
        assertNull(opts.x());
        assertNull(opts.y());
        assertFalse(opts.debug());
        assertEquals(WindowOptions.TitleBarStyle.DEFAULT, opts.titleBarStyle());
        assertNull(opts.trafficLightX());
        assertNull(opts.trafficLightY());
        assertFalse(opts.titlebarAppearsTransparent());
    }

    @Test
    @DisplayName("builder sets title and size")
    void builderTitleAndSize() {
        WindowOptions opts = WindowOptions.builder()
            .title("My App")
            .size(800, 600)
            .build();

        assertEquals("My App", opts.title());
        assertEquals(800, opts.width());
        assertEquals(600, opts.height());
    }

    @Test
    @DisplayName("builder sets individual width and height")
    void builderIndividualWidthHeight() {
        WindowOptions opts = WindowOptions.builder()
            .width(1920)
            .height(1080)
            .build();

        assertEquals(1920, opts.width());
        assertEquals(1080, opts.height());
    }

    @Test
    @DisplayName("builder sets min and max size")
    void builderMinMaxSize() {
        WindowOptions opts = WindowOptions.builder()
            .minSize(400, 300)
            .maxSize(1920, 1080)
            .build();

        assertEquals(400, opts.minWidth());
        assertEquals(300, opts.minHeight());
        assertEquals(1920, opts.maxWidth());
        assertEquals(1080, opts.maxHeight());
    }

    @Test
    @DisplayName("position sets x/y and disables center")
    void positionSetsXYAndDisablesCenter() {
        WindowOptions opts = WindowOptions.builder()
            .position(100, 200)
            .build();

        assertEquals(100, opts.x());
        assertEquals(200, opts.y());
        assertFalse(opts.center());
    }

    @Test
    @DisplayName("debug() shorthand enables debug")
    void debugShorthand() {
        WindowOptions opts = WindowOptions.builder()
            .debug()
            .build();

        assertTrue(opts.debug());
    }

    @Test
    @DisplayName("boolean flags can be toggled")
    void booleanFlags() {
        WindowOptions opts = WindowOptions.builder()
            .resizable(false)
            .fullscreen(true)
            .alwaysOnTop(true)
            .transparent(true)
            .decorations(false)
            .center(false)
            .build();

        assertFalse(opts.resizable());
        assertTrue(opts.fullscreen());
        assertTrue(opts.alwaysOnTop());
        assertTrue(opts.transparent());
        assertFalse(opts.decorations());
        assertFalse(opts.center());
    }

    @Test
    @DisplayName("hiddenInset sets title bar style and transparent titlebar")
    void hiddenInsetSetsStyleAndTransparent() {
        WindowOptions opts = WindowOptions.builder()
            .hiddenInset()
            .build();

        assertEquals(WindowOptions.TitleBarStyle.HIDDEN_INSET, opts.titleBarStyle());
        assertTrue(opts.titlebarAppearsTransparent());
    }

    @Test
    @DisplayName("titleBarStyle can be set explicitly")
    void titleBarStyleExplicit() {
        WindowOptions opts = WindowOptions.builder()
            .titleBarStyle(WindowOptions.TitleBarStyle.HIDDEN)
            .build();

        assertEquals(WindowOptions.TitleBarStyle.HIDDEN, opts.titleBarStyle());
    }

    @Test
    @DisplayName("trafficLightPosition sets coordinates")
    void trafficLightPosition() {
        WindowOptions opts = WindowOptions.builder()
            .trafficLightPosition(20, 24)
            .build();

        assertEquals(20, opts.trafficLightX());
        assertEquals(24, opts.trafficLightY());
    }

    @Test
    @DisplayName("titlebarAppearsTransparent can be set independently")
    void titlebarAppearsTransparentIndependent() {
        WindowOptions opts = WindowOptions.builder()
            .titlebarAppearsTransparent(true)
            .build();

        assertTrue(opts.titlebarAppearsTransparent());
        assertEquals(WindowOptions.TitleBarStyle.DEFAULT, opts.titleBarStyle());
    }
}
