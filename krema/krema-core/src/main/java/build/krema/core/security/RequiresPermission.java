package build.krema.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare permissions required by a command.
 * Commands annotated with this will be checked against granted permissions
 * before execution.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @RequiresPermission(Permission.FS_WRITE)
 * @KremaCommand
 * public void writeFile(String path, String content) {
 *     // ...
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {

    /**
     * The permissions required by this command.
     * All listed permissions must be granted for the command to execute.
     */
    Permission[] value();

    /**
     * If true, any of the listed permissions is sufficient.
     * If false (default), all permissions are required.
     */
    boolean anyOf() default false;
}
