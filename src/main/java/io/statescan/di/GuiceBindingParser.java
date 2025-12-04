package io.statescan.di;

import io.statescan.graph.CallGraph;
import io.statescan.graph.ClassNode;
import io.statescan.graph.MethodNode;
import io.statescan.graph.MethodRef;

import java.util.*;

/**
 * Extracts Guice dependency injection bindings from module classes.
 * <p>
 * Parses Guice module classes to extract interface-to-implementation bindings.
 * This enables the reachability analyzer to follow DI binding edges when
 * determining which classes are reachable from project roots.
 * <p>
 * Supported patterns:
 * <ul>
 *   <li>{@code bind(Interface.class).to(Implementation.class)}</li>
 *   <li>{@code bind(Interface.class).to(Implementation.class).in(Singleton.class)}</li>
 *   <li>{@code bind(Interface.class).toInstance(object)}</li>
 *   <li>{@code @Provides} methods - return type becomes the provided binding</li>
 *   <li>{@code install(new OtherModule())} - recursive module parsing</li>
 *   <li>Abstract module inheritance - parse parent configure() methods</li>
 * </ul>
 */
public class GuiceBindingParser {

    private static final String ABSTRACT_MODULE = "com.google.inject.AbstractModule";
    private static final String PRIVATE_MODULE = "com.google.inject.PrivateModule";
    private static final Set<String> GUICE_MODULE_BASES = Set.of(
            ABSTRACT_MODULE,
            PRIVATE_MODULE,
            "com.google.inject.Module"
    );

    private static final Set<String> GUICE_BINDING_METHODS = Set.of(
            "bind", "to", "toInstance", "toProvider", "in", "asEagerSingleton"
    );

    /**
     * Extracts all bindings from Guice module classes in the call graph.
     *
     * @param graph The call graph containing all scanned classes
     * @return Map of interface FQN to set of implementation FQNs
     */
    public Map<String, Set<String>> extractAllBindings(CallGraph graph) {
        Map<String, Set<String>> bindings = new HashMap<>();

        // Find all Guice module classes
        for (ClassNode cls : graph.allClasses()) {
            if (isGuiceModule(cls, graph)) {
                Map<String, Set<String>> moduleBindings = extractBindings(cls, graph);
                mergeBindings(bindings, moduleBindings);
            }
        }

        return bindings;
    }

    /**
     * Extracts all bindings from a single Guice module, including inherited bindings.
     *
     * @param moduleClass The module class to parse
     * @param graph       The call graph for looking up related classes
     * @return Map of interface FQN to set of implementation FQNs
     */
    public Map<String, Set<String>> extractBindings(ClassNode moduleClass, CallGraph graph) {
        Map<String, Set<String>> bindings = new HashMap<>();

        // 1. Trace inheritance chain to AbstractModule
        List<ClassNode> moduleChain = traceModuleChain(moduleClass, graph);

        // 2. Parse configure() at each level (parent first)
        for (ClassNode module : moduleChain) {
            parseConfigureMethod(module, bindings, graph);
            parseProvidesMethods(module, bindings);
        }

        // 3. Parse install() calls to find transitively installed modules
        Set<String> visited = new HashSet<>();
        for (ClassNode module : moduleChain) {
            parseInstallCalls(module, bindings, graph, visited);
        }

        return bindings;
    }

    /**
     * Traces the module inheritance chain from the given module up to AbstractModule.
     * Returns the list ordered parent-first (so bindings accumulate correctly).
     */
    private List<ClassNode> traceModuleChain(ClassNode module, CallGraph graph) {
        List<ClassNode> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ClassNode current = module;

        while (current != null && !visited.contains(current.fqn())) {
            visited.add(current.fqn());

            // Stop at Guice's base classes
            if (GUICE_MODULE_BASES.contains(current.fqn())) {
                break;
            }

            chain.add(current);

            // Walk up to superclass
            String superclass = current.superclass();
            if (superclass == null) break;

            current = graph.getClass(superclass).orElse(null);
        }

        // Reverse to get parent-first order
        Collections.reverse(chain);
        return chain;
    }

    /**
     * Parses the configure() method of a module to extract bindings.
     * <p>
     * Looks for patterns like:
     * <pre>
     * bind(Interface.class).to(Implementation.class)
     * bind(Interface.class).toInstance(...)
     * </pre>
     * <p>
     * Uses class constants (from LDC instructions) to identify the bound types.
     */
    private void parseConfigureMethod(ClassNode module, Map<String, Set<String>> bindings, CallGraph graph) {
        Optional<MethodNode> configureOpt = module.methods().stream()
                .filter(MethodNode::isGuiceConfigureMethod)
                .findFirst();

        if (configureOpt.isEmpty()) {
            return;
        }

        MethodNode configure = configureOpt.get();

        // Get all class constants referenced in configure()
        Set<String> classConstants = configure.classConstantRefs();
        if (classConstants.isEmpty()) {
            return;
        }

        // Get all method invocations to understand binding patterns
        Set<MethodRef> invocations = configure.invocations();

        // Look for bind() calls followed by to() calls
        boolean hasBind = invocations.stream()
                .anyMatch(inv -> "bind".equals(inv.name()));
        boolean hasTo = invocations.stream()
                .anyMatch(inv -> "to".equals(inv.name()) || "toInstance".equals(inv.name()));

        if (hasBind && hasTo) {
            // Heuristic: pair up class constants
            // In bind(A.class).to(B.class), A is the interface and B is the implementation
            // Class constants appear in order of LDC instructions

            List<String> constants = new ArrayList<>(classConstants);

            // Filter out common non-binding classes like Singleton.class, Scopes, etc.
            constants.removeIf(this::isGuiceInfrastructureClass);

            // Simple pairing: assume pairs of (interface, impl)
            for (int i = 0; i + 1 < constants.size(); i += 2) {
                String iface = constants.get(i);
                String impl = constants.get(i + 1);

                // Validate: impl should be a concrete class that could implement iface
                addBinding(bindings, iface, impl);
            }
        }
    }

    /**
     * Parses @Provides methods in the module.
     * The return type of the method becomes the binding.
     */
    private void parseProvidesMethods(ClassNode module, Map<String, Set<String>> bindings) {
        for (MethodNode method : module.methods()) {
            if (method.isProviderMethod()) {
                // Extract return type from descriptor
                String returnType = extractReturnType(method.descriptor());
                if (returnType != null && !isPrimitive(returnType)) {
                    // The provider method itself provides this type
                    // For reachability, we need to know that this module provides this type
                    // This is captured by the method's invocations
                    for (String classRef : method.classConstantRefs()) {
                        // If the method creates instances of classes, record them
                        addBinding(bindings, returnType, classRef);
                    }
                }
            }
        }
    }

    /**
     * Parses install() calls to find transitively installed modules.
     */
    private void parseInstallCalls(ClassNode module, Map<String, Set<String>> bindings,
                                   CallGraph graph, Set<String> visited) {
        if (visited.contains(module.fqn())) {
            return;
        }
        visited.add(module.fqn());

        Optional<MethodNode> configureOpt = module.methods().stream()
                .filter(MethodNode::isGuiceConfigureMethod)
                .findFirst();

        if (configureOpt.isEmpty()) {
            return;
        }

        MethodNode configure = configureOpt.get();

        // Look for install() calls
        boolean hasInstall = configure.invocations().stream()
                .anyMatch(inv -> "install".equals(inv.name()));

        if (!hasInstall) {
            return;
        }

        // The class constants in configure() include classes of installed modules
        for (String classRef : configure.classConstantRefs()) {
            if (visited.contains(classRef)) {
                continue;
            }

            Optional<ClassNode> installedModule = graph.getClass(classRef);
            if (installedModule.isPresent() && isGuiceModule(installedModule.get(), graph)) {
                // Recursively extract bindings from installed module
                Map<String, Set<String>> installedBindings = extractBindings(installedModule.get(), graph);
                mergeBindings(bindings, installedBindings);
            }
        }
    }

    /**
     * Checks if a class is a Guice module (extends AbstractModule, PrivateModule, or implements Module).
     */
    private boolean isGuiceModule(ClassNode cls, CallGraph graph) {
        // Direct check
        if (GUICE_MODULE_BASES.contains(cls.superclass())) {
            return true;
        }
        if (cls.interfaces().stream().anyMatch(i -> i.contains("Module"))) {
            return true;
        }

        // Check superclass chain
        String superclass = cls.superclass();
        Set<String> visited = new HashSet<>();

        while (superclass != null && !visited.contains(superclass)) {
            visited.add(superclass);

            if (GUICE_MODULE_BASES.contains(superclass)) {
                return true;
            }

            Optional<ClassNode> parent = graph.getClass(superclass);
            if (parent.isEmpty()) {
                break;
            }

            superclass = parent.get().superclass();
        }

        return false;
    }

    /**
     * Checks if a class is Guice infrastructure (not a user-defined binding target).
     */
    private boolean isGuiceInfrastructureClass(String className) {
        return className.startsWith("com.google.inject.") ||
                className.startsWith("javax.inject.") ||
                className.startsWith("jakarta.inject.") ||
                className.equals("java.lang.Class") ||
                className.startsWith("java.lang.annotation.");
    }

    /**
     * Extracts the return type from a method descriptor.
     * E.g., "(Ljava/lang/String;)Lcom/example/Foo;" returns "com.example.Foo"
     */
    private String extractReturnType(String descriptor) {
        if (descriptor == null) return null;

        int returnTypeStart = descriptor.lastIndexOf(')') + 1;
        if (returnTypeStart <= 0 || returnTypeStart >= descriptor.length()) {
            return null;
        }

        String returnDesc = descriptor.substring(returnTypeStart);

        // Handle object types
        if (returnDesc.startsWith("L") && returnDesc.endsWith(";")) {
            return returnDesc.substring(1, returnDesc.length() - 1).replace('/', '.');
        }

        // Handle array types
        if (returnDesc.startsWith("[L") && returnDesc.endsWith(";")) {
            return returnDesc.substring(2, returnDesc.length() - 1).replace('/', '.');
        }

        return null;
    }

    /**
     * Checks if a type descriptor represents a primitive type.
     */
    private boolean isPrimitive(String type) {
        if (type == null || type.isEmpty()) return true;
        return switch (type) {
            case "void", "boolean", "byte", "char", "short", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    /**
     * Adds a binding from interface to implementation.
     */
    private void addBinding(Map<String, Set<String>> bindings, String iface, String impl) {
        if (iface == null || impl == null) return;
        if (iface.equals(impl)) return; // Self-binding is not useful
        bindings.computeIfAbsent(iface, k -> new HashSet<>()).add(impl);
    }

    /**
     * Merges source bindings into target bindings.
     */
    private void mergeBindings(Map<String, Set<String>> target, Map<String, Set<String>> source) {
        source.forEach((iface, impls) ->
                target.computeIfAbsent(iface, k -> new HashSet<>()).addAll(impls));
    }
}
