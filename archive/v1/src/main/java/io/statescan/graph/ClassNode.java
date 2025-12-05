package io.statescan.graph;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a class in the analysis graph.
 *
 * @param fqn            Fully qualified class name (e.g., "com.company.MyClass")
 * @param superclass     Fully qualified name of superclass (null for java.lang.Object)
 * @param interfaces     Set of fully qualified interface names
 * @param methods        Set of methods declared in this class
 * @param fields         Set of fields declared in this class
 * @param annotations    Set of annotation class names on this class
 * @param isProjectClass True if this is a project class (not a dependency)
 * @param isInterface    True if this is an interface
 * @param isAbstract     True if this is abstract
 * @param isEnum         True if this is an enum
 * @param sourceFile     Source file name (from debug info, may be null)
 */
public record ClassNode(
        String fqn,
        String superclass,
        Set<String> interfaces,
        Set<MethodNode> methods,
        Set<FieldNode> fields,
        Set<String> annotations,
        boolean isProjectClass,
        boolean isInterface,
        boolean isAbstract,
        boolean isEnum,
        String sourceFile
) {
    /**
     * Compact constructor with validation.
     */
    public ClassNode {
        if (fqn == null || fqn.isBlank()) {
            throw new IllegalArgumentException("Class FQN cannot be null or blank");
        }
        if (interfaces == null) {
            interfaces = Set.of();
        } else {
            interfaces = Set.copyOf(interfaces);
        }
        if (methods == null) {
            methods = Set.of();
        } else {
            methods = Set.copyOf(methods);
        }
        if (fields == null) {
            fields = Set.of();
        } else {
            fields = Set.copyOf(fields);
        }
        if (annotations == null) {
            annotations = Set.of();
        } else {
            annotations = Set.copyOf(annotations);
        }
    }

    /**
     * Returns the simple class name (without package).
     */
    public String simpleName() {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    /**
     * Returns the package name.
     */
    public String packageName() {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(0, lastDot) : "";
    }

    /**
     * Checks if this class has a specific annotation.
     */
    public boolean hasAnnotation(String annotationClass) {
        return annotations.stream()
                .anyMatch(a -> a.equals(annotationClass) ||
                        a.endsWith("." + annotationClass) ||
                        a.endsWith("/" + annotationClass));
    }

    /**
     * Checks if this class is annotated as a singleton.
     */
    public boolean hasSingletonAnnotation() {
        return hasAnnotation("Singleton") ||
                hasAnnotation("ApplicationScoped");
    }

    /**
     * Checks if this class is annotated as session-scoped.
     */
    public boolean hasSessionScopedAnnotation() {
        return hasAnnotation("SessionScoped");
    }

    /**
     * Checks if this class is annotated as request-scoped.
     */
    public boolean hasRequestScopedAnnotation() {
        return hasAnnotation("RequestScoped");
    }

    /**
     * Checks if this class is a Guice module.
     */
    public boolean isGuiceModule() {
        if (superclass == null) return false;
        return superclass.contains("AbstractModule") ||
                superclass.contains("PrivateModule") ||
                interfaces.stream().anyMatch(i -> i.contains("Module"));
    }

    /**
     * Checks if this class is a REST resource/controller.
     */
    public boolean isRestResource() {
        return hasAnnotation("Path") ||
                hasAnnotation("RestController") ||
                hasAnnotation("Controller");
    }

    /**
     * Returns the method with the given name and descriptor.
     */
    public Optional<MethodNode> findMethod(String name, String descriptor) {
        return methods.stream()
                .filter(m -> m.name().equals(name) &&
                        (descriptor == null || m.descriptor().equals(descriptor)))
                .findFirst();
    }

    /**
     * Returns all methods with the given name (any descriptor).
     */
    public Set<MethodNode> findMethodsByName(String name) {
        return methods.stream()
                .filter(m -> m.name().equals(name))
                .collect(Collectors.toSet());
    }

    /**
     * Returns the field with the given name.
     */
    public Optional<FieldNode> findField(String name) {
        return fields.stream()
                .filter(f -> f.name().equals(name))
                .findFirst();
    }

    /**
     * Returns all static mutable fields.
     */
    public Set<FieldNode> staticMutableFields() {
        return fields.stream()
                .filter(FieldNode::isStaticMutable)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all potentially mutable instance fields.
     */
    public Set<FieldNode> mutableInstanceFields() {
        return fields.stream()
                .filter(f -> !f.isStatic() && f.isPotentiallyMutable())
                .collect(Collectors.toSet());
    }

    /**
     * Returns the Guice configure() method if present.
     */
    public Optional<MethodNode> guiceConfigureMethod() {
        return methods.stream()
                .filter(MethodNode::isGuiceConfigureMethod)
                .findFirst();
    }

    /**
     * Returns all @Provides methods.
     */
    public Set<MethodNode> providerMethods() {
        return methods.stream()
                .filter(MethodNode::isProviderMethod)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all REST endpoint methods.
     */
    public Set<MethodNode> restEndpointMethods() {
        return methods.stream()
                .filter(MethodNode::isRestEndpoint)
                .collect(Collectors.toSet());
    }

    /**
     * Builder for creating ClassNode instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String fqn;
        private String superclass;
        private Set<String> interfaces = Set.of();
        private Set<MethodNode> methods = Set.of();
        private Set<FieldNode> fields = Set.of();
        private Set<String> annotations = Set.of();
        private boolean isProjectClass;
        private boolean isInterface;
        private boolean isAbstract;
        private boolean isEnum;
        private String sourceFile;

        public Builder fqn(String fqn) {
            this.fqn = fqn;
            return this;
        }

        public Builder superclass(String superclass) {
            this.superclass = superclass;
            return this;
        }

        public Builder interfaces(Set<String> interfaces) {
            this.interfaces = interfaces;
            return this;
        }

        public Builder methods(Set<MethodNode> methods) {
            this.methods = methods;
            return this;
        }

        public Builder fields(Set<FieldNode> fields) {
            this.fields = fields;
            return this;
        }

        public Builder annotations(Set<String> annotations) {
            this.annotations = annotations;
            return this;
        }

        public Builder isProjectClass(boolean isProjectClass) {
            this.isProjectClass = isProjectClass;
            return this;
        }

        public Builder isInterface(boolean isInterface) {
            this.isInterface = isInterface;
            return this;
        }

        public Builder isAbstract(boolean isAbstract) {
            this.isAbstract = isAbstract;
            return this;
        }

        public Builder isEnum(boolean isEnum) {
            this.isEnum = isEnum;
            return this;
        }

        public Builder sourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public ClassNode build() {
            return new ClassNode(
                    fqn, superclass, interfaces, methods, fields,
                    annotations, isProjectClass, isInterface, isAbstract,
                    isEnum, sourceFile
            );
        }
    }
}
