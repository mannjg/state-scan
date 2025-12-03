package io.statescan.graph;

/**
 * Reference to a field, used for tracking field accesses.
 *
 * @param owner Fully qualified class name that owns this field
 * @param name  Field name
 * @param type  Field type descriptor (e.g., "Ljava/util/Map;")
 */
public record FieldRef(
        String owner,
        String name,
        String type
) {
    /**
     * Alternate constructor without type (for lookups).
     */
    public FieldRef(String owner, String name) {
        this(owner, name, null);
    }

    /**
     * Returns a human-readable signature.
     */
    public String toSignature() {
        if (type != null) {
            return typeToReadable(type) + " " + owner + "." + name;
        }
        return owner + "." + name;
    }

    /**
     * Returns short form without type.
     */
    public String toShortSignature() {
        return owner + "." + name;
    }

    /**
     * Converts a type descriptor to a readable format.
     */
    public static String typeToReadable(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return "?";
        }

        return switch (descriptor.charAt(0)) {
            case 'Z' -> "boolean";
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'S' -> "short";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'F' -> "float";
            case 'D' -> "double";
            case 'V' -> "void";
            case '[' -> typeToReadable(descriptor.substring(1)) + "[]";
            case 'L' -> {
                // Object type: Ljava/util/Map; -> java.util.Map
                String className = descriptor.substring(1, descriptor.length() - 1);
                yield className.replace('/', '.');
            }
            default -> descriptor;
        };
    }

    /**
     * Extracts the base type from an array type descriptor.
     */
    public static String getBaseType(String descriptor) {
        if (descriptor == null) return null;
        int i = 0;
        while (i < descriptor.length() && descriptor.charAt(i) == '[') {
            i++;
        }
        return descriptor.substring(i);
    }

    /**
     * Checks if the type is a collection type (Map, List, Set, etc.).
     */
    public boolean isCollectionType() {
        if (type == null) return false;
        String readable = typeToReadable(type);
        return readable.contains("Map") ||
                readable.contains("List") ||
                readable.contains("Set") ||
                readable.contains("Collection") ||
                readable.contains("Queue") ||
                readable.contains("Deque");
    }

    /**
     * Checks if the type is an atomic type (AtomicInteger, AtomicReference, etc.).
     */
    public boolean isAtomicType() {
        if (type == null) return false;
        String readable = typeToReadable(type);
        return readable.contains("Atomic") ||
                readable.contains("java.util.concurrent");
    }
}
