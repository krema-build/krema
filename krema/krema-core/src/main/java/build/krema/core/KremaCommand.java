package build.krema.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a Krema command that can be invoked from JavaScript.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * public class MyCommands {
 *     @KremaCommand
 *     public String greet(String name) {
 *         return "Hello, " + name + "!";
 *     }
 *
 *     @KremaCommand("custom_name")
 *     public int calculate(int a, int b) {
 *         return a + b;
 *     }
 * }
 * }</pre>
 *
 * <p>From JavaScript:</p>
 * <pre>{@code
 * const greeting = await window.krema.invoke('greet', { name: 'World' });
 * const result = await window.krema.invoke('custom_name', { a: 5, b: 3 });
 * }</pre>
 *
 * <p>Methods can have the following parameter types:</p>
 * <ul>
 *   <li>Primitives and their wrappers (int, Integer, boolean, Boolean, etc.)</li>
 *   <li>String</li>
 *   <li>POJOs (deserialized from JSON args)</li>
 *   <li>{@link build.krema.core.ipc.IpcHandler.IpcRequest} for full request access</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface KremaCommand {
    /**
     * The command name. If empty, the method name is used.
     */
    String value() default "";
}
