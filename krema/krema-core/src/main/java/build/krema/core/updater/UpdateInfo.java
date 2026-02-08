package build.krema.core.updater;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Information about an available update.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateInfo {

    private String version;
    private String releaseDate;
    private String releaseNotes;
    private String downloadUrl;
    private String signature;
    private long size;
    private boolean mandatory;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    @Override
    public String toString() {
        return String.format("UpdateInfo{version='%s', mandatory=%s}", version, mandatory);
    }
}
