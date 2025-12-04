package io.statescan.graph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The complete call graph and type hierarchy.
 * Provides efficient lookups for class relationships and method calls.
 */
public class CallGraph {

    private final Map<String, ClassNode> classes;

    // Derived indexes for efficient lookup
    private final Map<String, Set<String>> subtypes;      // parent -> direct children
    private final Map<String, Set<String>> supertypes;    // child -> all ancestors (transitive)
    private final Map<MethodRef, Set<MethodRef>> calledBy; // method -> methods that call it

    // DI binding edges: interface/abstract -> concrete implementations
    private final Map<String, Set<String>> diBindings;

    private CallGraph(
            Map<String, ClassNode> classes,
            Map<String, Set<String>> subtypes,
            Map<String, Set<String>> supertypes,
            Map<MethodRef, Set<MethodRef>> calledBy,
            Map<String, Set<String>> diBindings
    ) {
        this.classes = Map.copyOf(classes);
        this.subtypes = deepCopySetMap(subtypes);
        this.supertypes = deepCopySetMap(supertypes);
        this.calledBy = deepCopySetMap(calledBy);
        this.diBindings = deepCopySetMap(diBindings);
    }

    private static <K, V> Map<K, Set<V>> deepCopySetMap(Map<K, Set<V>> original) {
        return original.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Set.copyOf(e.getValue())
                ));
    }

    /**
     * Returns the class node for the given FQN, or empty if not found.
     */
    public Optional<ClassNode> getClass(String fqn) {
        return Optional.ofNullable(classes.get(fqn));
    }

    /**
     * Returns all classes in the graph.
     */
    public Collection<ClassNode> allClasses() {
        return classes.values();
    }

    /**
     * Returns all project classes (non-dependency).
     */
    public Collection<ClassNode> projectClasses() {
        return classes.values().stream()
                .filter(ClassNode::isProjectClass)
                .toList();
    }

    /**
     * Returns the number of classes in the graph.
     */
    public int classCount() {
        return classes.size();
    }

    /**
     * Returns direct subtypes (children) of the given class.
     */
    public Set<String> directSubtypes(String fqn) {
        return subtypes.getOrDefault(fqn, Set.of());
    }

    /**
     * Returns all transitive subtypes (descendants) of the given class.
     */
    public Set<String> allSubtypes(String fqn) {
        Set<String> result = new HashSet<>();
        collectSubtypesRecursive(fqn, result);
        return result;
    }

    private void collectSubtypesRecursive(String fqn, Set<String> result) {
        for (String child : directSubtypes(fqn)) {
            if (result.add(child)) {
                collectSubtypesRecursive(child, result);
            }
        }
    }

    /**
     * Returns all supertypes (ancestors) of the given class.
     */
    public Set<String> allSupertypes(String fqn) {
        return supertypes.getOrDefault(fqn, Set.of());
    }

    /**
     * Checks if 'child' is a subtype of 'parent' (directly or transitively).
     */
    public boolean isSubtypeOf(String child, String parent) {
        return allSupertypes(child).contains(parent);
    }

    /**
     * Returns all methods that call the given method.
     */
    public Set<MethodRef> callersOf(MethodRef method) {
        return calledBy.getOrDefault(method, Set.of());
    }

    /**
     * Returns implementations bound to the given interface via DI.
     * This includes both Guice bindings and CDI bean discovery.
     */
    public Set<String> getImplementations(String interfaceFqn) {
        return diBindings.getOrDefault(interfaceFqn, Set.of());
    }

    /**
     * Checks if the given type has DI bindings.
     */
    public boolean hasDIBinding(String type) {
        return diBindings.containsKey(type);
    }

    /**
     * Returns all DI bindings.
     */
    public Map<String, Set<String>> allDIBindings() {
        return Collections.unmodifiableMap(diBindings);
    }

    /**
     * Returns all classes that extend the given class (recursively).
     */
    public Set<ClassNode> classesExtending(String superclassFqn) {
        return allSubtypes(superclassFqn).stream()
                .map(classes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all classes implementing the given interface (recursively).
     */
    public Set<ClassNode> classesImplementing(String interfaceFqn) {
        return allSubtypes(interfaceFqn).stream()
                .map(classes::get)
                .filter(Objects::nonNull)
                .filter(c -> !c.isInterface())
                .collect(Collectors.toSet());
    }

    /**
     * Returns all classes with the given annotation.
     */
    public Set<ClassNode> classesWithAnnotation(String annotationClass) {
        return classes.values().stream()
                .filter(c -> c.hasAnnotation(annotationClass))
                .collect(Collectors.toSet());
    }

    /**
     * Returns all Guice module classes.
     */
    public Set<ClassNode> guiceModules() {
        return classes.values().stream()
                .filter(ClassNode::isGuiceModule)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all REST resource classes.
     */
    public Set<ClassNode> restResources() {
        return classes.values().stream()
                .filter(ClassNode::isRestResource)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all singleton classes (annotated).
     */
    public Set<ClassNode> singletonClasses() {
        return classes.values().stream()
                .filter(ClassNode::hasSingletonAnnotation)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all classes with static mutable fields.
     */
    public Set<ClassNode> classesWithStaticMutableFields() {
        return classes.values().stream()
                .filter(c -> !c.staticMutableFields().isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Creates a new CallGraph with additional DI bindings merged in.
     * This is used after bytecode scanning to add Guice/CDI binding information.
     */
    public CallGraph withDIBindings(Map<String, Set<String>> additionalBindings) {
        Map<String, Set<String>> mergedBindings = new HashMap<>(diBindings);
        additionalBindings.forEach((iface, impls) ->
                mergedBindings.computeIfAbsent(iface, k -> new HashSet<>()).addAll(impls));
        return new CallGraph(classes, subtypes, supertypes, calledBy, mergedBindings);
    }

    /**
     * Creates a filtered view of this graph containing only reachable classes.
     */
    public CallGraph filterTo(Set<String> reachableClasses) {
        Map<String, ClassNode> filteredClasses = classes.entrySet().stream()
                .filter(e -> reachableClasses.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<String, Set<String>> filteredSubtypes = subtypes.entrySet().stream()
                .filter(e -> reachableClasses.contains(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .filter(reachableClasses::contains)
                                .collect(Collectors.toSet())
                ));

        Map<String, Set<String>> filteredSupertypes = supertypes.entrySet().stream()
                .filter(e -> reachableClasses.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<MethodRef, Set<MethodRef>> filteredCalledBy = calledBy.entrySet().stream()
                .filter(e -> reachableClasses.contains(e.getKey().owner()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .filter(m -> reachableClasses.contains(m.owner()))
                                .collect(Collectors.toSet())
                ));

        Map<String, Set<String>> filteredDIBindings = diBindings.entrySet().stream()
                .filter(e -> reachableClasses.contains(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .filter(reachableClasses::contains)
                                .collect(Collectors.toSet())
                ));

        return new CallGraph(filteredClasses, filteredSubtypes, filteredSupertypes, filteredCalledBy, filteredDIBindings);
    }

    /**
     * Builder for constructing a CallGraph.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, ClassNode> classes = new HashMap<>();
        private final Map<String, Set<String>> subtypes = new HashMap<>();
        private final Map<String, Set<String>> supertypes = new HashMap<>();
        private final Map<MethodRef, Set<MethodRef>> calledBy = new HashMap<>();
        private final Map<String, Set<String>> diBindings = new HashMap<>();

        public Builder addClass(ClassNode classNode) {
            classes.put(classNode.fqn(), classNode);
            return this;
        }

        public Builder addSubtype(String parent, String child) {
            subtypes.computeIfAbsent(parent, k -> new HashSet<>()).add(child);
            return this;
        }

        public Builder addSupertype(String child, String parent) {
            supertypes.computeIfAbsent(child, k -> new HashSet<>()).add(parent);
            return this;
        }

        public Builder addCaller(MethodRef callee, MethodRef caller) {
            calledBy.computeIfAbsent(callee, k -> new HashSet<>()).add(caller);
            return this;
        }

        /**
         * Adds a single DI binding from interface to implementation.
         */
        public Builder addDIBinding(String interfaceFqn, String implementationFqn) {
            diBindings.computeIfAbsent(interfaceFqn, k -> new HashSet<>()).add(implementationFqn);
            return this;
        }

        /**
         * Adds multiple DI bindings at once.
         */
        public Builder addDIBindings(Map<String, Set<String>> bindings) {
            bindings.forEach((iface, impls) ->
                    diBindings.computeIfAbsent(iface, k -> new HashSet<>()).addAll(impls));
            return this;
        }

        /**
         * Builds the type hierarchy indexes from the class data.
         */
        public Builder buildHierarchy() {
            // Build direct subtype relationships
            for (ClassNode cls : classes.values()) {
                if (cls.superclass() != null) {
                    addSubtype(cls.superclass(), cls.fqn());
                }
                for (String iface : cls.interfaces()) {
                    addSubtype(iface, cls.fqn());
                }
            }

            // Build transitive supertype relationships
            for (String fqn : classes.keySet()) {
                Set<String> ancestors = new HashSet<>();
                collectAncestors(fqn, ancestors);
                supertypes.put(fqn, ancestors);
            }

            return this;
        }

        private void collectAncestors(String fqn, Set<String> ancestors) {
            ClassNode cls = classes.get(fqn);
            if (cls == null) return;

            if (cls.superclass() != null && ancestors.add(cls.superclass())) {
                collectAncestors(cls.superclass(), ancestors);
            }

            for (String iface : cls.interfaces()) {
                if (ancestors.add(iface)) {
                    collectAncestors(iface, ancestors);
                }
            }
        }

        /**
         * Builds the caller index from method invocation data.
         */
        public Builder buildCallerIndex() {
            for (ClassNode cls : classes.values()) {
                for (MethodNode method : cls.methods()) {
                    MethodRef caller = new MethodRef(cls.fqn(), method.name(), method.descriptor());
                    for (MethodRef callee : method.invocations()) {
                        addCaller(callee, caller);
                    }
                }
            }
            return this;
        }

        public CallGraph build() {
            return new CallGraph(classes, subtypes, supertypes, calledBy, diBindings);
        }
    }
}
