package build.krema.cli.bundle;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration for creating application bundles.
 *
 * @param appName The application name (used for bundle directory name)
 * @param identifier Bundle identifier (e.g., com.example.myapp)
 * @param version Application version
 * @param mainClass Fully qualified main class name
 * @param classpath Runtime classpath
 * @param libPath Path to native libraries
 * @param outputDir Directory where the bundle will be created
 * @param iconPath Optional path to application icon
 * @param deepLinkSchemes URL schemes for deep link protocol registration
 */
public record AppBundleConfig(
    String appName,
    String identifier,
    String version,
    String mainClass,
    String classpath,
    Path libPath,
    Path outputDir,
    Path iconPath,
    List<String> deepLinkSchemes
) {

    /**
     * Creates a builder for constructing AppBundleConfig instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String appName;
        private String identifier;
        private String version = "1.0.0";
        private String mainClass;
        private String classpath;
        private Path libPath;
        private Path outputDir;
        private Path iconPath;
        private List<String> deepLinkSchemes = List.of();

        public Builder appName(String appName) {
            this.appName = appName;
            return this;
        }

        public Builder identifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public Builder classpath(String classpath) {
            this.classpath = classpath;
            return this;
        }

        public Builder libPath(Path libPath) {
            this.libPath = libPath;
            return this;
        }

        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Builder iconPath(Path iconPath) {
            this.iconPath = iconPath;
            return this;
        }

        public Builder deepLinkSchemes(List<String> schemes) {
            this.deepLinkSchemes = schemes != null ? schemes : List.of();
            return this;
        }

        public AppBundleConfig build() {
            if (appName == null || appName.isBlank()) {
                throw new IllegalStateException("appName is required");
            }
            if (identifier == null || identifier.isBlank()) {
                throw new IllegalStateException("identifier is required");
            }
            if (mainClass == null || mainClass.isBlank()) {
                throw new IllegalStateException("mainClass is required");
            }
            if (outputDir == null) {
                throw new IllegalStateException("outputDir is required");
            }
            return new AppBundleConfig(
                appName, identifier, version, mainClass,
                classpath, libPath, outputDir, iconPath,
                deepLinkSchemes
            );
        }
    }
}
