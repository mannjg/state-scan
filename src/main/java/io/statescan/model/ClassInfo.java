package io.statescan.model;

import java.util.Map;
import java.util.Optional;

/**
 * Information about a scanned class.
 *
 * @param fqn         Fully qualified class name (e.g., "com.example.MyClass")
 * @param methods     Map of method key (name+descriptor) to MethodInfo
 * @param fields      Map of field name to field type FQN
 * @param isInterface Whether this is an interface
 */
public record ClassInfo(
    String fqn,
    Map<String, MethodInfo> methods,
    Map<String, String> fields,
    boolean isInterface
) {
    /**
     * Create a ClassInfo with immutable copies of maps.
     */
    public ClassInfo {
        methods = Map.copyOf(methods);
        fields = Map.copyOf(fields);
    }

    /**
     * Returns the simple class name (e.g., "MyClass" from "com.example.MyClass").
     */
    public String simpleName() {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    /**
     * Returns the package name (e.g., "com.example" from "com.example.MyClass").
     */
    public String packageName() {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(0, lastDot) : "";
    }

    /**
     * Find a method by name (returns first match if overloaded).
     */
    public Optional<MethodInfo> findMethodByName(String name) {
        return methods.values().stream()
            .filter(m -> m.name().equals(name))
            .findFirst();
    }

    /**
     * Get field type by name.
     */
    public Optional<String> getFieldType(String fieldName) {
        return Optional.ofNullable(fields.get(fieldName));
    }
}
