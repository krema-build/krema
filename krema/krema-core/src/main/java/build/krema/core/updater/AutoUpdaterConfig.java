package build.krema.core.updater;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for the {@link AutoUpdater}.
 *
 * @param endpoints list of update endpoint URL templates (supports {@code {{target}}},
 *                  {@code {{arch}}}, {@code {{current_version}}} variables)
 * @param currentVersion the current application version
 * @param publicKey base64-encoded Ed25519 public key for signature verification (may be null)
 * @param timeout connection/read timeout
 */
public record AutoUpdaterConfig(
    List<String> endpoints,
    String currentVersion,
    String publicKey,
    Duration timeout
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> endpoints = List.of();
        private String currentVersion = "0.0.0";
        private String publicKey;
        private Duration timeout = Duration.ofSeconds(30);

        public Builder endpoints(List<String> endpoints) {
            this.endpoints = endpoints;
            return this;
        }

        public Builder currentVersion(String currentVersion) {
            this.currentVersion = currentVersion;
            return this;
        }

        public Builder publicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public AutoUpdaterConfig build() {
            return new AutoUpdaterConfig(endpoints, currentVersion, publicKey, timeout);
        }
    }
}
