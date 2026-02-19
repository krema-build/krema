package build.krema.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Shared {@link ObjectMapper} instances for the Krema framework.
 *
 * <p>Jackson's {@code ObjectMapper} is thread-safe after configuration and
 * maintains internal type-resolution caches. Sharing a single instance
 * avoids redundant cache warm-up and reduces heap pressure.</p>
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ObjectMapper PRETTY_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private Json() {}

    /**
     * Returns the shared default {@link ObjectMapper}.
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Returns a shared {@link ObjectMapper} with pretty-printing enabled.
     */
    public static ObjectMapper prettyMapper() {
        return PRETTY_MAPPER;
    }
}
