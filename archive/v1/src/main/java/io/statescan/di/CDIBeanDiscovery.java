package io.statescan.di;

import io.statescan.graph.CallGraph;
import io.statescan.graph.ClassNode;
import io.statescan.graph.MethodNode;

import java.util.*;

/**
 * Discovers CDI (Contexts and Dependency Injection) bean bindings.
 * <p>
 * This class handles Jakarta EE / Quarkus CDI patterns where implementations
 * are discovered through annotations and interface hierarchies rather than
 * explicit bindings like in Guice.
 * <p>
 * Supported patterns:
 * <ul>
 *   <li>Classes annotated with scope annotations ({@code @ApplicationScoped}, {@code @Singleton}, etc.)</li>
 *   <li>Single-implementation interfaces (if an interface has exactly one CDI bean implementing it)</li>
 *   <li>{@code @Produces} methods that provide instances</li>
 *   <li>Abstract class implementations</li>
 * </ul>
 */
public class CDIBeanDiscovery {

    private static final Set<String> SCOPE_ANNOTATIONS = Set.of(
            // Jakarta CDI (modern)
            "jakarta.inject.Singleton",
            "jakarta.enterprise.context.ApplicationScoped",
            "jakarta.enterprise.context.RequestScoped",
            "jakarta.enterprise.context.SessionScoped",
            "jakarta.enterprise.context.Dependent",
            "jakarta.enterprise.context.ConversationScoped",
            // Javax CDI (legacy)
            "javax.inject.Singleton",
            "javax.enterprise.context.ApplicationScoped",
            "javax.enterprise.context.RequestScoped",
            "javax.enterprise.context.SessionScoped",
            "javax.enterprise.context.Dependent",
            "javax.enterprise.context.ConversationScoped",
            // Quarkus-specific
            "io.quarkus.arc.DefaultBean"
    );

    private static final Set<String> PRODUCER_ANNOTATIONS = Set.of(
            "jakarta.enterprise.inject.Produces",
            "javax.enterprise.inject.Produces"
    );

    /**
     * Discovers all CDI bindings from beans in the call graph.
     *
     * @param graph The call graph containing all scanned classes
     * @return Map of interface/abstract class FQN to set of implementation FQNs
     */
    public Map<String, Set<String>> discoverBindings(CallGraph graph) {
        Map<String, Set<String>> bindings = new HashMap<>();

        // Find all CDI beans (classes with scope annotations)
        List<ClassNode> beans = graph.allClasses().stream()
                .filter(this::isCDIBean)
                .toList();

        // Build interface-to-implementations map
        Map<String, Set<ClassNode>> interfaceImpls = new HashMap<>();

        for (ClassNode bean : beans) {
            // Track implementations of interfaces
            for (String iface : bean.interfaces()) {
                interfaceImpls.computeIfAbsent(iface, k -> new HashSet<>()).add(bean);
            }

            // Track implementations of abstract superclasses
            if (bean.superclass() != null) {
                Optional<ClassNode> parent = graph.getClass(bean.superclass());
                if (parent.isPresent() && parent.get().isAbstract()) {
                    interfaceImpls.computeIfAbsent(parent.get().fqn(), k -> new HashSet<>()).add(bean);
                }
            }
        }

        // Convert to bindings map
        // For CDI, we include all implementations (CDI uses qualifiers to disambiguate)
        for (Map.Entry<String, Set<ClassNode>> entry : interfaceImpls.entrySet()) {
            String iface = entry.getKey();
            Set<ClassNode> impls = entry.getValue();

            Set<String> implFqns = new HashSet<>();
            for (ClassNode impl : impls) {
                implFqns.add(impl.fqn());
            }

            if (!implFqns.isEmpty()) {
                bindings.put(iface, implFqns);
            }
        }

        // Parse @Produces methods
        parseProducerMethods(graph.allClasses(), bindings, graph);

        return bindings;
    }

    /**
     * Checks if a class is a CDI bean (has a scope annotation).
     */
    private boolean isCDIBean(ClassNode cls) {
        return cls.annotations().stream()
                .anyMatch(a -> SCOPE_ANNOTATIONS.stream()
                        .anyMatch(scope -> a.equals(scope) || a.endsWith("." + getSimpleName(scope))));
    }

    /**
     * Parses @Produces methods across all classes to find producer bindings.
     */
    private void parseProducerMethods(Collection<ClassNode> classes,
                                      Map<String, Set<String>> bindings,
                                      CallGraph graph) {
        for (ClassNode cls : classes) {
            for (MethodNode method : cls.methods()) {
                if (isProducerMethod(method)) {
                    String returnType = extractReturnType(method.descriptor());
                    if (returnType != null && !isPrimitive(returnType)) {
                        // The producer class provides this type
                        // Mark that this class can provide the return type
                        bindings.computeIfAbsent(returnType, k -> new HashSet<>()).add(cls.fqn());

                        // Also track what types are created in the producer method
                        for (String classRef : method.classConstantRefs()) {
                            if (!isJavaLangClass(classRef)) {
                                bindings.computeIfAbsent(returnType, k -> new HashSet<>()).add(classRef);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if a method is a CDI producer method.
     */
    private boolean isProducerMethod(MethodNode method) {
        return method.annotations().stream()
                .anyMatch(a -> PRODUCER_ANNOTATIONS.stream()
                        .anyMatch(prod -> a.equals(prod) || a.endsWith("." + getSimpleName(prod))));
    }

    /**
     * Extracts the return type from a method descriptor.
     */
    private String extractReturnType(String descriptor) {
        if (descriptor == null) return null;

        int returnTypeStart = descriptor.lastIndexOf(')') + 1;
        if (returnTypeStart <= 0 || returnTypeStart >= descriptor.length()) {
            return null;
        }

        String returnDesc = descriptor.substring(returnTypeStart);

        // Handle object types: Lcom/example/Foo; -> com.example.Foo
        if (returnDesc.startsWith("L") && returnDesc.endsWith(";")) {
            return returnDesc.substring(1, returnDesc.length() - 1).replace('/', '.');
        }

        // Handle array types: [Lcom/example/Foo; -> com.example.Foo
        if (returnDesc.startsWith("[L") && returnDesc.endsWith(";")) {
            return returnDesc.substring(2, returnDesc.length() - 1).replace('/', '.');
        }

        return null;
    }

    /**
     * Gets the simple name from a fully qualified class name.
     */
    private String getSimpleName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    /**
     * Checks if a type is a primitive.
     */
    private boolean isPrimitive(String type) {
        if (type == null || type.isEmpty()) return true;
        return switch (type) {
            case "void", "boolean", "byte", "char", "short", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    /**
     * Checks if a class is a java.lang class (not useful for bindings).
     */
    private boolean isJavaLangClass(String className) {
        return className != null && className.startsWith("java.lang.");
    }
}
