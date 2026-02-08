package build.krema.core;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Functional interface for statically-generated command dispatch.
 * Takes JSON args directly without reflection.
 */
@FunctionalInterface
public interface CommandInvoker {
    Object invoke(JsonNode args) throws Exception;
}
