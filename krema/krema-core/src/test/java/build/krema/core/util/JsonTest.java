package build.krema.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Json")
class JsonTest {

    @Test
    @DisplayName("mapper() returns the same instance on every call")
    void mapperReturnsSingleton() {
        assertSame(Json.mapper(), Json.mapper());
    }

    @Test
    @DisplayName("prettyMapper() returns the same instance on every call")
    void prettyMapperReturnsSingleton() {
        assertSame(Json.prettyMapper(), Json.prettyMapper());
    }

    @Test
    @DisplayName("mapper() and prettyMapper() are different instances")
    void mapperAndPrettyMapperAreDifferent() {
        assertNotSame(Json.mapper(), Json.prettyMapper());
    }

    @Test
    @DisplayName("mapper() serializes objects correctly")
    void mapperSerializes() throws JsonProcessingException {
        String json = Json.mapper().writeValueAsString(Map.of("key", "value"));
        assertEquals("{\"key\":\"value\"}", json);
    }

    @Test
    @DisplayName("prettyMapper() produces indented output")
    void prettyMapperIndents() throws JsonProcessingException {
        String json = Json.prettyMapper().writeValueAsString(Map.of("a", 1));
        assertTrue(json.contains("\n"));
        assertTrue(json.contains("  "));
    }

    @Test
    @DisplayName("mapper() can deserialize JSON")
    void mapperDeserializes() throws JsonProcessingException {
        ObjectMapper mapper = Json.mapper();
        Map<?, ?> result = mapper.readValue("{\"x\":42}", Map.class);
        assertEquals(42, result.get("x"));
    }
}
