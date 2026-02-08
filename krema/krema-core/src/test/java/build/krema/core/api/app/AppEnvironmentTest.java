package build.krema.core.api.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import build.krema.core.api.app.AppEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AppEnvironment")
class AppEnvironmentTest {

    @Test
    @DisplayName("getEnvironment returns name and vars")
    void getEnvironmentReturnsNameAndVars() {
        Map<String, String> vars = Map.of("API_URL", "http://localhost:8080");
        AppEnvironment env = new AppEnvironment("development", vars);

        Map<String, Object> result = env.getEnvironment();

        assertEquals("development", result.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, String> resultVars = (Map<String, String>) result.get("vars");
        assertEquals("http://localhost:8080", resultVars.get("API_URL"));
    }

    @Test
    @DisplayName("getEnvironmentName returns name")
    void getEnvironmentNameReturnsName() {
        AppEnvironment env = new AppEnvironment("staging", Map.of());

        assertEquals("staging", env.getEnvironmentName());
    }

    @Test
    @DisplayName("getEnvironmentVar returns value")
    void getEnvironmentVarReturnsValue() {
        AppEnvironment env = new AppEnvironment("development", Map.of("KEY", "value"));

        assertEquals("value", env.getEnvironmentVar("KEY"));
    }

    @Test
    @DisplayName("getEnvironmentVar returns null for missing key")
    void getEnvironmentVarReturnsNullForMissing() {
        AppEnvironment env = new AppEnvironment("development", Map.of());

        assertNull(env.getEnvironmentVar("MISSING"));
    }

    @Test
    @DisplayName("defaults to production when name is null")
    void defaultsToProductionWhenNull() {
        AppEnvironment env = new AppEnvironment(null, null);

        assertEquals("production", env.getEnvironmentName());
        assertTrue(env.getEnvironment().containsKey("vars"));
    }
}
