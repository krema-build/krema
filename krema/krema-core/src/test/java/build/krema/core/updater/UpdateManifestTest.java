package build.krema.core.updater;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import build.krema.core.updater.UpdateInfo;
import build.krema.core.updater.UpdateManifest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UpdateManifest")
class UpdateManifestTest {

    @Test
    @DisplayName("resolve multi-platform manifest for matching target")
    void resolveMultiPlatformMatchingTarget() throws IOException {
        String json = """
                {
                  "version": "1.1.0",
                  "notes": "Bug fixes and improvements",
                  "pub_date": "2024-01-15T10:30:00Z",
                  "platforms": {
                    "darwin-aarch64": {
                      "signature": "sig-darwin",
                      "url": "https://example.com/update-darwin.tar.gz",
                      "size": 52428800
                    },
                    "windows-x86_64": {
                      "signature": "sig-windows",
                      "url": "https://example.com/update-windows.zip",
                      "size": 48000000
                    }
                  }
                }
                """;

        UpdateManifest manifest = UpdateManifest.parse(json);
        UpdateInfo info = manifest.resolve("darwin-aarch64");

        assertNotNull(info);
        assertEquals("1.1.0", info.getVersion());
        assertEquals("https://example.com/update-darwin.tar.gz", info.getDownloadUrl());
        assertEquals("sig-darwin", info.getSignature());
        assertEquals(52428800L, info.getSize());
        assertEquals("Bug fixes and improvements", info.getReleaseNotes());
        assertEquals("2024-01-15T10:30:00Z", info.getReleaseDate());
    }

    @Test
    @DisplayName("resolve multi-platform manifest for missing target returns null")
    void resolveMultiPlatformMissingTarget() throws IOException {
        String json = """
                {
                  "version": "1.1.0",
                  "platforms": {
                    "darwin-aarch64": {
                      "signature": "sig",
                      "url": "https://example.com/update.tar.gz",
                      "size": 100
                    }
                  }
                }
                """;

        UpdateManifest manifest = UpdateManifest.parse(json);
        UpdateInfo info = manifest.resolve("linux-x86_64");

        assertNull(info);
    }

    @Test
    @DisplayName("resolve simple format manifest")
    void resolveSimpleFormat() throws IOException {
        String json = """
                {
                  "version": "2.0.0",
                  "downloadUrl": "https://example.com/update.zip",
                  "signature": "simple-sig",
                  "size": 10000,
                  "mandatory": true,
                  "releaseDate": "2024-06-01",
                  "releaseNotes": "Major release"
                }
                """;

        UpdateManifest manifest = UpdateManifest.parse(json);
        UpdateInfo info = manifest.resolve("any-target");

        assertNotNull(info);
        assertEquals("2.0.0", info.getVersion());
        assertEquals("https://example.com/update.zip", info.getDownloadUrl());
        assertEquals("simple-sig", info.getSignature());
        assertEquals(10000L, info.getSize());
        assertTrue(info.isMandatory());
        assertEquals("2024-06-01", info.getReleaseDate());
        assertEquals("Major release", info.getReleaseNotes());
    }

    @Test
    @DisplayName("simple format falls back to pubDate when releaseDate is null")
    void simpleFormatReleaseDateFallback() throws IOException {
        String json = """
                {
                  "version": "1.0.0",
                  "downloadUrl": "https://example.com/update.zip",
                  "pub_date": "2024-03-01T00:00:00Z"
                }
                """;

        UpdateManifest manifest = UpdateManifest.parse(json);
        UpdateInfo info = manifest.resolve("any");

        assertEquals("2024-03-01T00:00:00Z", info.getReleaseDate());
    }

    @Test
    @DisplayName("simple format falls back to notes when releaseNotes is null")
    void simpleFormatReleaseNotesFallback() throws IOException {
        String json = """
                {
                  "version": "1.0.0",
                  "downloadUrl": "https://example.com/update.zip",
                  "notes": "Fallback notes"
                }
                """;

        UpdateManifest manifest = UpdateManifest.parse(json);
        UpdateInfo info = manifest.resolve("any");

        assertEquals("Fallback notes", info.getReleaseNotes());
    }

    @Test
    @DisplayName("isMultiPlatform returns false for null platforms")
    void isMultiPlatformNullPlatforms() throws IOException {
        String json = """
                {
                  "version": "1.0.0",
                  "downloadUrl": "https://example.com/update.zip"
                }
                """;

        UpdateManifest manifest = UpdateManifest.parse(json);
        assertFalse(manifest.isMultiPlatform());
    }

    @Test
    @DisplayName("isMultiPlatform returns false for empty platforms")
    void isMultiPlatformEmptyPlatforms() throws IOException {
        String json = """
                {
                  "version": "1.0.0",
                  "platforms": {}
                }
                """;

        UpdateManifest manifest = UpdateManifest.parse(json);
        assertFalse(manifest.isMultiPlatform());
    }

    @Test
    @DisplayName("malformed JSON throws IOException")
    void malformedJsonThrows() {
        assertThrows(IOException.class, () -> UpdateManifest.parse("{not valid json"));
    }
}
