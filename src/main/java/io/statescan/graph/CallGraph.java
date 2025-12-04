package io.statescan.graph;

import io.statescan.di.BindingKey;

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

    // DI binding edges: BindingKey (type + qualifier) -> concrete implementations
    private final Map<BindingKey, Set<String>> diBindings;

    // Index for efficient unqualified lookups: type -> all implementations (across all qualifiers)
    private final Map<String, Set<String>> diBindingsByType;

    private CallGraph(
            Map<String, ClassNode> classes,
            Map<String, Set<String>> subtypes,
            Map<String, Set<String>> supertypes,
            Map<MethodRef, Set<MethodRef>> calledBy,
            Map<BindingKey, Set<String>> diBindings
    ) {
        this.classes = Map.copyOf(classes);
        this.subtypes = deepCopySetMap(subtypes);
        this.supertypes = deepCopySetMap(supertypes);
        this.calledBy = deepCopySetMap(calledBy);
        this.diBindings = deepCopyBindingMap(diBindings);
        this.diBindingsByType = buildTypeIndex(this.diBindings);
    }

    private static <K, V> Map<K, Set<V>> deepCopySetMap(Map<K, Set<V>> original) {
        return original.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Set.copyOf(e.getValue())
                ));
    }

    private static Map<BindingKey, Set<String>> deepCopyBindingMap(Map<BindingKey, Set<String>> original) {
        return original.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Set.copyOf(e.getValue())
                ));
    }

    /**
     * Builds an index from type to all implementations across all qualifiers.
     */
    private static Map<String, Set<String>> buildTypeIndex(Map<BindingKey, Set<String>> bindings) {
        Map<String, Set<String>> index = new HashMap<>();
        for (Map.Entry<BindingKey, Set<String>> entry : bindings.entrySet()) {
            String type = entry.getKey().type();
            index.computeIfAbsent(type, k -> new HashSet<>()).addAll(entry.getValue());
        }
        // Make immutable
        return index.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
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
     * Returns implementations bound to the given interface via DI (unqualified lookup).
     * This aggregates implementations across all qualifiers for the type.
     * For precise qualifier matching, use {@link #getImplementations(String, String)}.
     */
    public Set<String> getImplementations(String interfaceFqn) {
        return diBindingsByType.getOrDefault(interfaceFqn, Set.of());
    }

    /**
     * Returns implementations bound to the given interface with a specific qualifier.
     *
     * @param interfaceFqn The interface/type fully qualified name
     * @param qualifier The qualifier simple name (e.g., "ExternalFooService"), or null for unqualified
     * @return Set of implementation FQNs, or empty set if no binding found
     */
    public Set<String> getImplementations(String interfaceFqn, String qualifier) {
        BindingKey key = BindingKey.of(interfaceFqn, qualifier);
        return diBindings.getOrDefault(key, Set.of());
    }

    /**
     * Returns implementations for the given binding key.
     *
     * @param key The binding key (type + optional qualifier)
     * @return Set of implementation FQNs, or empty set if no binding found
     */
    public Set<String> getImplementations(BindingKey key) {
        return diBindings.getOrDefault(key, Set.of());
    }

    /**
     * Checks if the given type has DI bindings (any qualifier).
     */
    public boolean hasDIBinding(String type) {
        return diBindingsByType.containsKey(type);
    }

    /**
     * Checks if the given binding key has DI bindings.
     */
    public boolean hasDIBinding(BindingKey key) {
        return diBindings.containsKey(key);
    }

    /**
     * Returns all DI bindings (keyed by BindingKey).
     */
    public Map<BindingKey, Set<String>> allDIBindings() {
        return Collections.unmodifiableMap(diBindings);
    }

    /**
     * Returns all DI bindings as a simple type-to-implementations map (ignoring qualifiers).
     * This is for backward compatibility with code that doesn't need qualifier awareness.
     */
    public Map<String, Set<String>> allDIBindingsByType() {
        return Collections.unmodifiableMap(diBindingsByType);
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
     * Creates a new CallGraph with additional DI bindings merged in (BindingKey-based).
     * This is the preferred method for adding Guice bindings with qualifier support.
     */
    public CallGraph withDIBindings(Map<BindingKey, Set<String>> additionalBindings) {
        Map<BindingKey, Set<String>> mergedBindings = new HashMap<>(diBindings);
        additionalBindings.forEach((key, impls) ->
                mergedBindings.computeIfAbsent(key, k -> new HashSet<>()).addAll(impls));
        return new CallGraph(classes, subtypes, supertypes, calledBy, mergedBindings);
    }

    /**
     * Creates a new CallGraph with additional DI bindings merged in (type-only, unqualified).
     * This is for backward compatibility with CDI discovery that doesn't use qualifiers yet.
     */
    public CallGraph withUnqualifiedDIBindings(Map<String, Set<String>> additionalBindings) {
        Map<BindingKey, Set<String>> mergedBindings = new HashMap<>(diBindings);
        additionalBindings.forEach((type, impls) ->
                mergedBindings.computeIfAbsent(BindingKey.unqualified(type), k -> new HashSet<>()).addAll(impls));
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

        Map<BindingKey, Set<String>> filteredDIBindings = diBindings.entrySet().stream()
                .filter(e -> reachableClasses.contains(e.getKey().type()))
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
        private final Map<BindingKey, Set<String>> diBindings = new HashMap<>();

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
         * Adds a single DI binding from interface to implementation (unqualified).
         */
        public Builder addDIBinding(String interfaceFqn, String implementationFqn) {
            diBindings.computeIfAbsent(BindingKey.unqualified(interfaceFqn), k -> new HashSet<>()).add(implementationFqn);
            return this;
        }

        /**
         * Adds a single DI binding with qualifier support.
         */
        public Builder addDIBinding(BindingKey key, String implementationFqn) {
            diBindings.computeIfAbsent(key, k -> new HashSet<>()).add(implementationFqn);
            return this;
        }

        /**
         * Adds multiple DI bindings at once (BindingKey-based).
         */
        public Builder addDIBindings(Map<BindingKey, Set<String>> bindings) {
            bindings.forEach((key, impls) ->
                    diBindings.computeIfAbsent(key, k -> new HashSet<>()).addAll(impls));
            return this;
        }

        /**
         * Adds multiple DI bindings at once (unqualified, for backward compatibility).
         */
        public Builder addUnqualifiedDIBindings(Map<String, Set<String>> bindings) {
            bindings.forEach((type, impls) ->
                    diBindings.computeIfAbsent(BindingKey.unqualified(type), k -> new HashSet<>()).addAll(impls));
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
