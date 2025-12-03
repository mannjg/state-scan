package io.statescan.graph;

import java.util.Set;

/**
 * Represents a method in a class.
 *
 * @param name          Method name (e.g., "configure", "<init>")
 * @param descriptor    Method descriptor (e.g., "(Ljava/lang/String;)V")
 * @param invocations   Set of methods this method calls
 * @param fieldAccesses Set of fields this method accesses
 * @param annotations   Set of annotation class names on this method
 * @param isStatic      Whether the method is static
 * @param isPublic      Whether the method is public
 * @param isAbstract    Whether the method is abstract
 */
public record MethodNode(
        String name,
        String descriptor,
        Set<MethodRef> invocations,
        Set<FieldRef> fieldAccesses,
        Set<String> annotations,
        boolean isStatic,
        boolean isPublic,
        boolean isAbstract
) {
    /**
     * Compact constructor with validation.
     */
    public MethodNode {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Method name cannot be null or blank");
        }
        if (invocations == null) {
            invocations = Set.of();
        } else {
            invocations = Set.copyOf(invocations);
        }
        if (fieldAccesses == null) {
            fieldAccesses = Set.of();
        } else {
            fieldAccesses = Set.copyOf(fieldAccesses);
        }
        if (annotations == null) {
            annotations = Set.of();
        } else {
            annotations = Set.copyOf(annotations);
        }
    }

    /**
     * Checks if this is a constructor.
     */
    public boolean isConstructor() {
        return "<init>".equals(name);
    }

    /**
     * Checks if this is a static initializer.
     */
    public boolean isStaticInitializer() {
        return "<clinit>".equals(name);
    }

    /**
     * Checks if this method has a specific annotation.
     */
    public boolean hasAnnotation(String annotationClass) {
        return annotations.stream()
                .anyMatch(a -> a.equals(annotationClass) ||
                        a.endsWith("." + annotationClass) ||
                        a.endsWith("/" + annotationClass));
    }

    /**
     * Checks if this method is annotated as a REST endpoint.
     */
    public boolean isRestEndpoint() {
        return hasAnnotation("GET") ||
                hasAnnotation("POST") ||
                hasAnnotation("PUT") ||
                hasAnnotation("DELETE") ||
                hasAnnotation("PATCH") ||
                hasAnnotation("GetMapping") ||
                hasAnnotation("PostMapping") ||
                hasAnnotation("PutMapping") ||
                hasAnnotation("DeleteMapping") ||
                hasAnnotation("RequestMapping");
    }

    /**
     * Checks if this method is annotated as a Guice @Provides method.
     */
    public boolean isProviderMethod() {
        return hasAnnotation("Provides");
    }

    /**
     * Checks if this is likely the Guice configure() method.
     */
    public boolean isGuiceConfigureMethod() {
        return "configure".equals(name) && "()V".equals(descriptor);
    }

    /**
     * Returns a unique key for this method within its class.
     */
    public String methodKey() {
        return name + descriptor;
    }

    /**
     * Returns a human-readable signature.
     */
    public String toSignature() {
        return name + descriptor;
    }

    /**
     * Builder for creating MethodNode instances with mutable sets.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String descriptor;
        private Set<MethodRef> invocations = Set.of();
        private Set<FieldRef> fieldAccesses = Set.of();
        private Set<String> annotations = Set.of();
        private boolean isStatic;
        private boolean isPublic;
        private boolean isAbstract;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder descriptor(String descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public Builder invocations(Set<MethodRef> invocations) {
            this.invocations = invocations;
            return this;
        }

        public Builder fieldAccesses(Set<FieldRef> fieldAccesses) {
            this.fieldAccesses = fieldAccesses;
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

        public Builder isPublic(boolean isPublic) {
            this.isPublic = isPublic;
            return this;
        }

        public Builder isAbstract(boolean isAbstract) {
            this.isAbstract = isAbstract;
            return this;
        }

        public MethodNode build() {
            return new MethodNode(
                    name, descriptor, invocations, fieldAccesses,
                    annotations, isStatic, isPublic, isAbstract
            );
        }
    }
}
