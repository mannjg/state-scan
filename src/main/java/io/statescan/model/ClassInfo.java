package io.statescan.model;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Information about a scanned class.
 *
 * @param fqn                    Fully qualified class name (e.g., "com.example.MyClass")
 * @param methods                Map of method key (name+descriptor) to MethodInfo
 * @param fields                 Map of field name to field type FQN
 * @param isInterface            Whether this is an interface
 * @param isAbstract             Whether this is an abstract class
 * @param superClass             FQN of the superclass (null for java.lang.Object)
 * @param implementedInterfaces  Set of interface FQNs directly implemented by this class
 */
public record ClassInfo(
    String fqn,
    Map<String, MethodInfo> methods,
    Map<String, String> fields,
    boolean isInterface,
    boolean isAbstract,
    String superClass,
    Set<String> implementedInterfaces
) {
    /**
     * Create a ClassInfo with immutable copies of maps and sets.
     */
    public ClassInfo {
        methods = Map.copyOf(methods);
        fields = Map.copyOf(fields);
        implementedInterfaces = implementedInterfaces != null ? Set.copyOf(implementedInterfaces) : Set.of();
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

    /**
     * Returns true if this is a concrete class (not interface, not abstract).
     */
    public boolean isConcrete() {
        return !isInterface && !isAbstract;
    }

    /**
     * Returns true if this class needs implementation resolution
     * (is interface or abstract class).
     */
    public boolean needsResolution() {
        return isInterface || isAbstract;
    }
}
