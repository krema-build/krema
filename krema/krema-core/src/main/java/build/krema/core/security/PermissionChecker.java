package build.krema.core.security;

import java.lang.reflect.Method;
import java.util.*;

import build.krema.core.util.Logger;

/**
 * Checks and enforces permissions for command execution.
 */
public class PermissionChecker {

    private static final Logger LOG = new Logger("PermissionChecker");

    private final Set<String> grantedPermissions;
    private final boolean enforcePermissions;

    public PermissionChecker(Collection<String> grantedPermissions, boolean enforce) {
        this.grantedPermissions = new HashSet<>(grantedPermissions);
        this.enforcePermissions = enforce;
    }

    public PermissionChecker(Collection<String> grantedPermissions) {
        this(grantedPermissions, true);
    }

    /**
     * Creates a permissive checker that allows all operations.
     */
    public static PermissionChecker allowAll() {
        return new PermissionChecker(Set.of("*"), true);
    }

    /**
     * Checks if a method has permission to execute.
     *
     * @param method The method to check
     * @throws SecurityException if permission is denied
     */
    public void check(Method method) throws SecurityException {
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        if (annotation == null) {
            return; // No permissions required
        }

        Permission[] required = annotation.value();
        boolean anyOf = annotation.anyOf();

        if (anyOf) {
            checkAnyOf(method, required);
        } else {
            checkAllOf(method, required);
        }
    }

    private void checkAllOf(Method method, Permission[] required) {
        List<Permission> denied = new ArrayList<>();

        for (Permission permission : required) {
            if (!isGranted(permission)) {
                denied.add(permission);
            }
        }

        if (!denied.isEmpty()) {
            String deniedKeys = denied.stream()
                .map(Permission::getKey)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

            String message = String.format(
                "Permission denied for '%s': requires [%s]",
                method.getName(),
                deniedKeys
            );

            LOG.warn(message);

            if (enforcePermissions) {
                throw new SecurityException(message);
            }
        }
    }

    private void checkAnyOf(Method method, Permission[] required) {
        for (Permission permission : required) {
            if (isGranted(permission)) {
                return; // At least one permission granted
            }
        }

        String requiredKeys = Arrays.stream(required)
            .map(Permission::getKey)
            .reduce((a, b) -> a + ", " + b)
            .orElse("");

        String message = String.format(
            "Permission denied for '%s': requires any of [%s]",
            method.getName(),
            requiredKeys
        );

        LOG.warn(message);

        if (enforcePermissions) {
            throw new SecurityException(message);
        }
    }

    /**
     * Checks if a specific permission is granted.
     */
    public boolean isGranted(Permission permission) {
        return isGranted(permission.getKey());
    }

    /**
     * Checks if a specific permission key is granted.
     */
    public boolean isGranted(String permissionKey) {
        // Check for wildcard
        if (grantedPermissions.contains("*")) {
            return true;
        }

        // Direct match
        if (grantedPermissions.contains(permissionKey)) {
            return true;
        }

        // Check for partial wildcards (e.g., "fs:*" covers "fs:read")
        for (String granted : grantedPermissions) {
            if (granted.endsWith(":*")) {
                String prefix = granted.substring(0, granted.length() - 1);
                if (permissionKey.startsWith(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Grants a permission.
     */
    public void grant(String permissionKey) {
        grantedPermissions.add(permissionKey);
        LOG.debug("Granted permission: %s", permissionKey);
    }

    /**
     * Grants a permission.
     */
    public void grant(Permission permission) {
        grant(permission.getKey());
    }

    /**
     * Revokes a permission.
     */
    public void revoke(String permissionKey) {
        grantedPermissions.remove(permissionKey);
        LOG.debug("Revoked permission: %s", permissionKey);
    }

    /**
     * Returns all granted permissions.
     */
    public Set<String> getGrantedPermissions() {
        return Collections.unmodifiableSet(grantedPermissions);
    }

    /**
     * Returns a list of permissions required by a method.
     */
    public static List<Permission> getRequiredPermissions(Method method) {
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        if (annotation == null) {
            return List.of();
        }
        return Arrays.asList(annotation.value());
    }
}
