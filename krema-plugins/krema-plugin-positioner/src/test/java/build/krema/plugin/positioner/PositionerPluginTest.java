package build.krema.plugin.positioner;

import java.util.List;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Positioner Plugin")
class PositionerPluginTest {

    private PositionerPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new PositionerPlugin();
    }

    @Nested
    @DisplayName("Plugin metadata")
    class Metadata {

        @Test
        @DisplayName("returns correct ID")
        void returnsCorrectId() {
            assertEquals("krema.positioner", plugin.getId());
        }

        @Test
        @DisplayName("returns correct name")
        void returnsCorrectName() {
            assertEquals("Positioner", plugin.getName());
        }

        @Test
        @DisplayName("returns required permissions")
        void returnsRequiredPermissions() {
            assertEquals(List.of("window:manage"), plugin.getRequiredPermissions());
        }

        @Test
        @DisplayName("returns command handlers")
        void returnsCommandHandlers() {
            assertFalse(plugin.getCommandHandlers().isEmpty());
        }
    }

    @Nested
    @DisplayName("Position enum")
    class PositionEnum {

        @Test
        @DisplayName("has all 9 positions")
        void hasAllPositions() {
            assertEquals(9, PositionerPlugin.Position.values().length);
        }

        @Test
        @DisplayName("parses hyphenated names to enum values")
        void parsesHyphenatedNames() {
            assertEquals(PositionerPlugin.Position.TOP_LEFT,
                PositionerPlugin.Position.valueOf("top-left".toUpperCase().replace("-", "_")));
            assertEquals(PositionerPlugin.Position.BOTTOM_RIGHT,
                PositionerPlugin.Position.valueOf("bottom-right".toUpperCase().replace("-", "_")));
            assertEquals(PositionerPlugin.Position.CENTER,
                PositionerPlugin.Position.valueOf("center".toUpperCase().replace("-", "_")));
            assertEquals(PositionerPlugin.Position.LEFT_CENTER,
                PositionerPlugin.Position.valueOf("left-center".toUpperCase().replace("-", "_")));
            assertEquals(PositionerPlugin.Position.RIGHT_CENTER,
                PositionerPlugin.Position.valueOf("right-center".toUpperCase().replace("-", "_")));
        }

        @Test
        @DisplayName("throws for invalid position string")
        void throwsForInvalidPosition() {
            assertThrows(IllegalArgumentException.class, () ->
                PositionerPlugin.Position.valueOf("INVALID_POSITION"));
        }
    }

    @Nested
    @DisplayName("MoveToRequest record")
    class MoveToRequestRecord {

        @Test
        @DisplayName("stores position correctly")
        void storesPosition() {
            var req = new PositionerPlugin.MoveToRequest("top-left");
            assertEquals("top-left", req.position());
        }
    }
}
