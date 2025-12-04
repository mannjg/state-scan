package io.statescan.graph;

import java.util.Set;

/**
 * Represents a field in a class.
 *
 * @param name        Field name
 * @param type        Field type descriptor (e.g., "Ljava/util/Map;")
 * @param annotations Set of annotation class names on this field
 * @param isStatic    Whether the field is static
 * @param isFinal     Whether the field is final
 * @param isPrivate   Whether the field is private
 * @param isVolatile  Whether the field is volatile
 */
public record FieldNode(
        String name,
        String type,
        Set<String> annotations,
        boolean isStatic,
        boolean isFinal,
        boolean isPrivate,
        boolean isVolatile
) {
    /**
     * Compact constructor with validation.
     */
    public FieldNode {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Field name cannot be null or blank");
        }
        if (annotations == null) {
            annotations = Set.of();
        } else {
            annotations = Set.copyOf(annotations);
        }
    }

    /**
     * Returns the type in human-readable format.
     */
    public String readableType() {
        return FieldRef.typeToReadable(type);
    }

    /**
     * Extracts the fully qualified class name from the type descriptor.
     * Returns null for primitive types.
     */
    public String extractTypeName() {
        if (type == null) return null;

        // Skip array dimensions
        int start = 0;
        while (start < type.length() && type.charAt(start) == '[') {
            start++;
        }

        if (start >= type.length()) return null;

        char typeChar = type.charAt(start);
        if (typeChar == 'L') {
            // Object type: Lcom/example/Class; -> com.example.Class
            int end = type.indexOf(';', start);
            if (end > start) {
                return type.substring(start + 1, end).replace('/', '.');
            }
        }

        // Primitive type
        return null;
    }

    /**
     * Checks if this field could hold mutable state.
     * Non-final non-primitive fields, or final mutable collections.
     */
    public boolean isPotentiallyMutable() {
        // Non-final fields are always potentially mutable
        if (!isFinal) {
            return true;
        }

        // Final fields of mutable types (collections, maps) are still mutable
        if (type == null) return false;

        String readable = readableType();

        // Check for mutable collection types
        if (readable.contains("Map") ||
                readable.contains("List") ||
                readable.contains("Set") ||
                readable.contains("Collection") ||
                readable.contains("Queue") ||
                readable.contains("Deque")) {
            return true;
        }

        // Check for atomic types
        if (readable.contains("Atomic")) {
            return true;
        }

        // Check for StringBuilder/StringBuffer
        if (readable.contains("StringBuilder") || readable.contains("StringBuffer")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if this is a static mutable field (high risk).
     */
    public boolean isStaticMutable() {
        return isStatic && isPotentiallyMutable();
    }

    /**
     * Checks if this is likely a constant (static final primitive or String).
     */
    public boolean isConstant() {
        if (!isStatic || !isFinal) {
            return false;
        }

        if (type == null) return false;

        // Primitive types
        if (type.length() == 1 && "ZBCSIJFD".indexOf(type.charAt(0)) >= 0) {
            return true;
        }

        // String
        if ("Ljava/lang/String;".equals(type)) {
            return true;
        }

        // Boxed primitives
        String readable = readableType();
        return readable.startsWith("java.lang.") &&
                (readable.endsWith("Integer") ||
                        readable.endsWith("Long") ||
                        readable.endsWith("Boolean") ||
                        readable.endsWith("Double") ||
                        readable.endsWith("Float") ||
                        readable.endsWith("Short") ||
                        readable.endsWith("Byte") ||
                        readable.endsWith("Character"));
    }

    /**
     * Checks if this field is a Logger (commonly static final but safe).
     */
    public boolean isLogger() {
        if (type == null) return false;
        String readable = readableType();
        return readable.contains("Logger") ||
                readable.contains("Log");
    }

    /**
     * Checks if this field has a specific annotation.
     */
    public boolean hasAnnotation(String annotationClass) {
        return annotations.stream()
                .anyMatch(a -> a.equals(annotationClass) || a.endsWith("." + annotationClass));
    }

    /**
     * Builder for creating FieldNode instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String type;
        private Set<String> annotations = Set.of();
        private boolean isStatic;
        private boolean isFinal;
        private boolean isPrivate;
        private boolean isVolatile;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder annotations(Set<String> annotations) {
            this.annotations = annotations;
            return this;
        }

        public Builder isStatic(boolean isStatic) {
            this.isStatic = isStatic;
            return this;
        }

        public Builder isFinal(boolean isFinal) {
            this.isFinal = isFinal;
            return this;
        }

        public Builder isPrivate(boolean isPrivate) {
            this.isPrivate = isPrivate;
            return this;
        }

        public Builder isVolatile(boolean isVolatile) {
            this.isVolatile = isVolatile;
            return this;
        }

        public FieldNode build() {
            return new FieldNode(name, type, annotations, isStatic, isFinal, isPrivate, isVolatile);
        }
    }
}
