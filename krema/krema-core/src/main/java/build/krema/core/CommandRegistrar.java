package build.krema.core;

import java.util.Map;

/**
 * Interface for compile-time generated command registrars.
 * Each generated registrar provides static invokers for a specific command handler class,
 * eliminating the need for reflection at runtime.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}.</p>
 */
public interface CommandRegistrar {

    /**
     * Returns the class this registrar handles.
     */
    Class<?> targetType();

    /**
     * Creates a map of command-name to invoker for the given instance.
     *
     * @param instance the command handler object (must be assignable to {@link #targetType()})
     * @return map of command names to their invokers
     */
    Map<String, CommandInvoker> createInvokers(Object instance);
}
