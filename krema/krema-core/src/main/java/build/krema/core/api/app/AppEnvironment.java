package build.krema.core.api.app;

import java.util.Collections;
import java.util.Map;

import build.krema.core.KremaCommand;

/**
 * Provides the current environment profile and custom variables to the frontend.
 */
public class AppEnvironment {

    private final String name;
    private final Map<String, String> vars;

    public AppEnvironment(String name, Map<String, String> vars) {
        this.name = name != null ? name : "production";
        this.vars = vars != null ? Collections.unmodifiableMap(vars) : Collections.emptyMap();
    }

    @KremaCommand("app:getEnvironment")
    public Map<String, Object> getEnvironment() {
        return Map.of("name", name, "vars", vars);
    }

    @KremaCommand("app:getEnvironmentName")
    public String getEnvironmentName() {
        return name;
    }

    @KremaCommand("app:getEnvironmentVar")
    public String getEnvironmentVar(String key) {
        return vars.get(key);
    }
}
