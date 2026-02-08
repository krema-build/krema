package build.krema.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import build.krema.cli.DotEnvLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DotEnvLoader")
class DotEnvLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("loads base .env file")
    void loadsBaseEnvFile() throws IOException {
        Files.writeString(tempDir.resolve(".env"), """
                APP_NAME=MyApp
                PORT=3000
                """);

        Map<String, String> vars = DotEnvLoader.load(tempDir, "development");

        assertEquals("MyApp", vars.get("APP_NAME"));
        assertEquals("3000", vars.get("PORT"));
    }

    @Test
    @DisplayName("profile overrides base values")
    void profileOverridesBase() throws IOException {
        Files.writeString(tempDir.resolve(".env"), """
                API_URL=http://localhost:8080
                DEBUG=true
                """);
        Files.writeString(tempDir.resolve(".env.staging"), """
                API_URL=https://staging.example.com
                """);

        Map<String, String> vars = DotEnvLoader.load(tempDir, "staging");

        assertEquals("https://staging.example.com", vars.get("API_URL"));
        assertEquals("true", vars.get("DEBUG"));
    }

    @Test
    @DisplayName("handles quoted values")
    void handlesQuotedValues() throws IOException {
        Files.writeString(tempDir.resolve(".env"), """
                DOUBLE_QUOTED="hello world"
                SINGLE_QUOTED='hello world'
                UNQUOTED=hello
                """);

        Map<String, String> vars = DotEnvLoader.load(tempDir, null);

        assertEquals("hello world", vars.get("DOUBLE_QUOTED"));
        assertEquals("hello world", vars.get("SINGLE_QUOTED"));
        assertEquals("hello", vars.get("UNQUOTED"));
    }

    @Test
    @DisplayName("ignores comments and blank lines")
    void ignoresCommentsAndBlankLines() throws IOException {
        Files.writeString(tempDir.resolve(".env"), """
                # This is a comment
                KEY1=value1

                # Another comment
                KEY2=value2
                """);

        Map<String, String> vars = DotEnvLoader.load(tempDir, null);

        assertEquals(2, vars.size());
        assertEquals("value1", vars.get("KEY1"));
        assertEquals("value2", vars.get("KEY2"));
    }

    @Test
    @DisplayName("no files returns empty map")
    void noFilesReturnsEmptyMap() {
        Map<String, String> vars = DotEnvLoader.load(tempDir, "development");

        assertTrue(vars.isEmpty());
    }

    @Test
    @DisplayName("handles missing profile file")
    void handlesMissingProfileFile() throws IOException {
        Files.writeString(tempDir.resolve(".env"), """
                KEY=base_value
                """);

        Map<String, String> vars = DotEnvLoader.load(tempDir, "nonexistent");

        assertEquals(1, vars.size());
        assertEquals("base_value", vars.get("KEY"));
    }

    @Test
    @DisplayName("handles equals sign in value")
    void handlesEqualsInValue() throws IOException {
        Files.writeString(tempDir.resolve(".env"), """
                CONNECTION=postgres://user:pass@host/db?ssl=true
                """);

        Map<String, String> vars = DotEnvLoader.load(tempDir, null);

        assertEquals("postgres://user:pass@host/db?ssl=true", vars.get("CONNECTION"));
    }
}
