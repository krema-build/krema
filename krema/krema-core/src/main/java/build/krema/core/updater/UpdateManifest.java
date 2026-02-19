package build.krema.core.updater;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import build.krema.core.util.Json;

import java.io.IOException;
import java.util.Map;

/**
 * Multi-platform update manifest that supports two JSON formats:
 *
 * <p><b>Multi-platform format</b> (Tauri-compatible):</p>
 * <pre>{@code
 * {
 *   "version": "1.1.0",
 *   "notes": "Bug fixes",
 *   "pub_date": "2024-01-15T10:30:00Z",
 *   "platforms": {
 *     "darwin-aarch64": { "signature": "...", "url": "https://...", "size": 52428800 },
 *     "windows-x86_64": { "signature": "...", "url": "https://...", "size": 48000000 }
 *   }
 * }
 * }</pre>
 *
 * <p><b>Simple format</b> (backward compat):</p>
 * <pre>{@code
 * {
 *   "version": "1.1.0",
 *   "downloadUrl": "https://...",
 *   "signature": "...",
 *   "size": 52428800,
 *   "mandatory": false
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateManifest {

    private String version;
    private String notes;

    @JsonProperty("pub_date")
    private String pubDate;

    private Map<String, PlatformEntry> platforms;

    // Simple format fields
    private String downloadUrl;
    private String releaseDate;
    private String releaseNotes;
    private String signature;
    private long size;
    private boolean mandatory;

    /**
     * Resolves this manifest to an {@link UpdateInfo} for the given target platform.
     *
     * @param target the platform target string (e.g. "darwin-aarch64")
     * @return the resolved UpdateInfo, or null if the target is not found in a multi-platform manifest
     */
    public UpdateInfo resolve(String target) {
        if (platforms != null && !platforms.isEmpty()) {
            return resolveMultiPlatform(target);
        }
        return resolveSimple();
    }

    private UpdateInfo resolveMultiPlatform(String target) {
        PlatformEntry entry = platforms.get(target);
        if (entry == null) {
            return null;
        }

        UpdateInfo info = new UpdateInfo();
        info.setVersion(version);
        info.setReleaseNotes(notes);
        info.setReleaseDate(pubDate);
        info.setDownloadUrl(entry.getUrl());
        info.setSignature(entry.getSignature());
        info.setSize(entry.getSize());
        info.setMandatory(false);
        return info;
    }

    private UpdateInfo resolveSimple() {
        UpdateInfo info = new UpdateInfo();
        info.setVersion(version);
        info.setDownloadUrl(downloadUrl);
        info.setSignature(signature);
        info.setSize(size);
        info.setMandatory(mandatory);
        info.setReleaseDate(releaseDate != null ? releaseDate : pubDate);
        info.setReleaseNotes(releaseNotes != null ? releaseNotes : notes);
        return info;
    }

    /**
     * Returns true if this manifest uses the multi-platform format.
     */
    public boolean isMultiPlatform() {
        return platforms != null && !platforms.isEmpty();
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getPubDate() { return pubDate; }
    public void setPubDate(String pubDate) { this.pubDate = pubDate; }
    public Map<String, PlatformEntry> getPlatforms() { return platforms; }
    public void setPlatforms(Map<String, PlatformEntry> platforms) { this.platforms = platforms; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
    public String getReleaseNotes() { return releaseNotes; }
    public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

    /**
     * A platform-specific entry in the multi-platform manifest.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlatformEntry {
        private String signature;
        private String url;
        private long size;

        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
    }

    /**
     * Parses a JSON string into an UpdateManifest.
     */
    public static UpdateManifest parse(String json) throws IOException {
        return Json.mapper().readValue(json, UpdateManifest.class);
    }
}
