package build.krema.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import build.krema.core.ipc.IpcHandler.IpcRequest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Registry for @KremaCommand annotated methods.
 * Handles scanning, registration, and invocation of commands.
 *
 * <p>Supports two registration paths:</p>
 * <ul>
 *   <li><b>Static</b>: Uses compile-time generated {@link CommandRegistrar} implementations
 *       discovered via {@link ServiceLoader}. No reflection needed at runtime.</li>
 *   <li><b>Reflection</b>: Falls back to scanning for {@link KremaCommand} annotations
 *       when no registrar is available for a class.</li>
 * </ul>
 */
public class CommandRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, CommandHandler> handlers = new HashMap<>();
    private final Map<Class<?>, CommandRegistrar> registrars = new HashMap<>();

    public CommandRegistry() {
        loadRegistrars();
    }

    private void loadRegistrars() {
        for (CommandRegistrar registrar : ServiceLoader.load(CommandRegistrar.class)) {
            registrars.put(registrar.targetType(), registrar);
        }
    }

    /**
     * Registers all @KremaCommand methods from the given objects.
     */
    public void register(Object... commandObjects) {
        for (Object obj : commandObjects) {
            registerObject(obj);
        }
    }

    /**
     * Registers all @KremaCommand methods from a single object.
     * Uses a static registrar if available, otherwise falls back to reflection.
     */
    private void registerObject(Object obj) {
        Class<?> clazz = obj.getClass();
        CommandRegistrar registrar = registrars.get(clazz);

        if (registrar != null) {
            registerStatic(obj, registrar);
        } else {
            registerReflective(obj);
        }
    }

    private void registerStatic(Object obj, CommandRegistrar registrar) {
        Class<?> clazz = obj.getClass();
        Map<String, CommandInvoker> invokers = registrar.createInvokers(obj);

        for (Map.Entry<String, CommandInvoker> entry : invokers.entrySet()) {
            String name = entry.getKey();

            if (handlers.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate command name: " + name);
            }

            handlers.put(name, new StaticCommandHandler(entry.getValue()));

            System.out.println("[Krema] Registered command: " + name + " -> " +
                clazz.getSimpleName() + " (static)");
        }
    }

    private void registerReflective(Object obj) {
        Class<?> clazz = obj.getClass();

        for (Method method : clazz.getDeclaredMethods()) {
            KremaCommand annotation = method.getAnnotation(KremaCommand.class);
            if (annotation != null) {
                String name = annotation.value().isEmpty() ? method.getName() : annotation.value();

                if (handlers.containsKey(name)) {
                    throw new IllegalArgumentException("Duplicate command name: " + name);
                }

                method.setAccessible(true);
                handlers.put(name, new ReflectiveCommandHandler(obj, method));

                System.out.println("[Krema] Registered command: " + name + " -> " +
                    clazz.getSimpleName() + "." + method.getName() + "() (reflection)");
            }
        }
    }

    /**
     * Invokes a command with the given request.
     *
     * @return The command result
     * @throws CommandException if the command fails
     */
    public Object invoke(IpcRequest request) throws CommandException {
        String command = request.getCommand();
        CommandHandler handler = handlers.get(command);

        if (handler == null) {
            throw new CommandException("Unknown command: " + command);
        }

        return handler.invoke(request);
    }

    /**
     * Returns the number of registered commands.
     */
    public int size() {
        return handlers.size();
    }

    /**
     * Returns true if a command with the given name is registered.
     */
    public boolean hasCommand(String name) {
        return handlers.containsKey(name);
    }

    /**
     * Returns a list of all registered command names.
     */
    public List<String> getCommandNames() {
        return new ArrayList<>(handlers.keySet());
    }

    /**
     * Common interface for command handlers (both static and reflective).
     */
    private interface CommandHandler {
        Object invoke(IpcRequest request) throws CommandException;
    }

    /**
     * Handler that delegates to a compile-time generated {@link CommandInvoker}.
     */
    private static class StaticCommandHandler implements CommandHandler {
        private final CommandInvoker invoker;

        StaticCommandHandler(CommandInvoker invoker) {
            this.invoker = invoker;
        }

        @Override
        public Object invoke(IpcRequest request) throws CommandException {
            try {
                return invoker.invoke(request.getArgs());
            } catch (CommandException e) {
                throw e;
            } catch (Exception e) {
                throw new CommandException("Command failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Handler that uses reflection to invoke @KremaCommand methods.
     */
    private static class ReflectiveCommandHandler implements CommandHandler {
        private final Object instance;
        private final Method method;
        private final Parameter[] parameters;

        ReflectiveCommandHandler(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
            this.parameters = method.getParameters();
        }

        @Override
        public Object invoke(IpcRequest request) throws CommandException {
            try {
                Object[] args = resolveArguments(request);
                return method.invoke(instance, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                throw new CommandException("Command failed: " + cause.getMessage(), cause);
            } catch (Exception e) {
                throw new CommandException("Failed to invoke command: " + e.getMessage(), e);
            }
        }

        private Object[] resolveArguments(IpcRequest request) throws Exception {
            JsonNode args = request.getArgs();
            Object[] result = new Object[parameters.length];

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                Class<?> type = param.getType();
                String name = param.getName();

                // Special case: IpcRequest gives full access
                if (type == IpcRequest.class) {
                    result[i] = request;
                    continue;
                }

                // If only one parameter and it's a POJO, deserialize entire args
                if (parameters.length == 1 && isComplexType(type)) {
                    result[i] = MAPPER.treeToValue(args, type);
                    continue;
                }

                // Get the value from args by parameter name
                JsonNode value = args != null ? args.get(name) : null;

                if (value == null || value.isNull()) {
                    result[i] = getDefaultValue(type);
                } else {
                    result[i] = convertValue(value, type);
                }
            }

            return result;
        }

        private boolean isComplexType(Class<?> type) {
            return !type.isPrimitive() &&
                   !type.equals(String.class) &&
                   !Number.class.isAssignableFrom(type) &&
                   !type.equals(Boolean.class) &&
                   !type.equals(Character.class);
        }

        private Object convertValue(JsonNode node, Class<?> type) throws Exception {
            if (type == String.class) {
                return node.asText();
            } else if (type == int.class || type == Integer.class) {
                return node.asInt();
            } else if (type == long.class || type == Long.class) {
                return node.asLong();
            } else if (type == double.class || type == Double.class) {
                return node.asDouble();
            } else if (type == float.class || type == Float.class) {
                return (float) node.asDouble();
            } else if (type == boolean.class || type == Boolean.class) {
                return node.asBoolean();
            } else if (type == short.class || type == Short.class) {
                return (short) node.asInt();
            } else if (type == byte.class || type == Byte.class) {
                return (byte) node.asInt();
            } else if (type == char.class || type == Character.class) {
                String text = node.asText();
                return text.isEmpty() ? '\0' : text.charAt(0);
            } else {
                // Complex type - deserialize from JSON
                return MAPPER.treeToValue(node, type);
            }
        }

        private Object getDefaultValue(Class<?> type) {
            if (type.isPrimitive()) {
                if (type == boolean.class) return false;
                if (type == char.class) return '\0';
                if (type == byte.class) return (byte) 0;
                if (type == short.class) return (short) 0;
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == float.class) return 0.0f;
                if (type == double.class) return 0.0;
            }
            return null;
        }
    }

    /**
     * Exception thrown when a command fails.
     */
    public static class CommandException extends Exception {
        public CommandException(String message) {
            super(message);
        }

        public CommandException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
