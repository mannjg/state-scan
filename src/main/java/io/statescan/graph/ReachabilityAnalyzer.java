package io.statescan.graph;

import io.statescan.di.QualifierExtractor;

import java.util.*;

/**
 * Analyzes reachability from project classes to determine which classes are actually used.
 * This enables "tree-shaking" - removing classes that aren't reachable from the project code.
 */
public class ReachabilityAnalyzer {

    private final CallGraph graph;
    private final List<String> projectPackagePrefixes;

    /**
     * Creates a reachability analyzer.
     *
     * @param graph                The complete call graph
     * @param projectPackagePrefix Package prefix to identify project classes (e.g., "com.company")
     */
    public ReachabilityAnalyzer(CallGraph graph, String projectPackagePrefix) {
        this(graph, projectPackagePrefix != null && !projectPackagePrefix.isEmpty()
                ? List.of(projectPackagePrefix) : List.of());
    }

    /**
     * Creates a reachability analyzer with multiple package prefixes.
     *
     * @param graph                  The complete call graph
     * @param projectPackagePrefixes Package prefixes to identify project classes
     */
    public ReachabilityAnalyzer(CallGraph graph, List<String> projectPackagePrefixes) {
        this.graph = graph;
        this.projectPackagePrefixes = projectPackagePrefixes != null ? List.copyOf(projectPackagePrefixes) : List.of();
    }

    /**
     * Performs reachability analysis starting from all project classes.
     * Returns the set of fully qualified class names that are reachable.
     */
    public Set<String> analyzeReachability() {
        Set<String> reachable = new HashSet<>();
        Queue<String> workQueue = new LinkedList<>();

        // Start with all project classes as roots
        for (ClassNode cls : graph.allClasses()) {
            if (isProjectClass(cls)) {
                if (reachable.add(cls.fqn())) {
                    workQueue.add(cls.fqn());
                }
            }
        }

        // BFS through all edges
        while (!workQueue.isEmpty()) {
            String current = workQueue.poll();
            ClassNode currentClass = graph.getClass(current).orElse(null);

            if (currentClass == null) {
                continue;
            }

            // Follow superclass edge
            if (currentClass.superclass() != null) {
                addIfNew(currentClass.superclass(), reachable, workQueue);
            }

            // Follow interface edges
            for (String iface : currentClass.interfaces()) {
                addIfNew(iface, reachable, workQueue);
            }

            // Follow method invocation edges
            for (MethodNode method : currentClass.methods()) {
                for (MethodRef invocation : method.invocations()) {
                    addIfNew(invocation.owner(), reachable, workQueue);
                }

                // Follow field access edges (for field type classes)
                for (FieldRef fieldAccess : method.fieldAccesses()) {
                    addIfNew(fieldAccess.owner(), reachable, workQueue);
                    // Also add the field's type if it's a reference type
                    String fieldType = extractTypeFromDescriptor(fieldAccess.type());
                    if (fieldType != null) {
                        addIfNew(fieldType, reachable, workQueue);
                    }
                }
            }

            // Follow field type edges and DI bindings for @Inject fields
            for (FieldNode field : currentClass.fields()) {
                String fieldType = extractTypeFromDescriptor(field.type());
                if (fieldType != null) {
                    addIfNew(fieldType, reachable, workQueue);
                    
                    // For @Inject fields, also follow qualified DI bindings
                    if (QualifierExtractor.isInjectionPoint(field.annotations())) {
                        String qualifier = QualifierExtractor.extractQualifier(field.annotations());
                        Set<String> implementations = graph.getImplementations(fieldType, qualifier);
                        for (String impl : implementations) {
                            addIfNew(impl, reachable, workQueue);
                        }
                    }
                }
            }

            // Follow DI binding edges (Guice, CDI) at class level for non-field bindings
            // This handles cases where a class is bound to an interface without field injection
            Set<String> implementations = graph.getImplementations(current);
            for (String impl : implementations) {
                addIfNew(impl, reachable, workQueue);
            }
        }

        return reachable;
    }

    /**
     * Returns a filtered CallGraph containing only reachable classes.
     */
    public CallGraph getReachableGraph() {
        Set<String> reachable = analyzeReachability();
        return graph.filterTo(reachable);
    }

    /**
     * Analyzes reachability with additional detail about how each class was reached.
     */
    public ReachabilityResult analyzeWithPaths() {
        Map<String, ReachabilityInfo> reachabilityMap = new HashMap<>();
        Queue<String> workQueue = new LinkedList<>();

        // Start with all project classes as roots
        for (ClassNode cls : graph.allClasses()) {
            if (isProjectClass(cls)) {
                reachabilityMap.put(cls.fqn(), new ReachabilityInfo(
                        cls.fqn(),
                        ReachabilityReason.PROJECT_CLASS,
                        null,
                        0
                ));
                workQueue.add(cls.fqn());
            }
        }

        // BFS through all edges
        while (!workQueue.isEmpty()) {
            String current = workQueue.poll();
            ReachabilityInfo currentInfo = reachabilityMap.get(current);
            int nextDepth = currentInfo.depth() + 1;

            ClassNode currentClass = graph.getClass(current).orElse(null);
            if (currentClass == null) {
                continue;
            }

            // Follow superclass edge
            if (currentClass.superclass() != null) {
                addWithReason(currentClass.superclass(), ReachabilityReason.EXTENDS,
                        current, nextDepth, reachabilityMap, workQueue);
            }

            // Follow interface edges
            for (String iface : currentClass.interfaces()) {
                addWithReason(iface, ReachabilityReason.IMPLEMENTS,
                        current, nextDepth, reachabilityMap, workQueue);
            }

            // Follow method invocation edges
            for (MethodNode method : currentClass.methods()) {
                for (MethodRef invocation : method.invocations()) {
                    addWithReason(invocation.owner(), ReachabilityReason.METHOD_CALL,
                            current + "." + method.name(), nextDepth, reachabilityMap, workQueue);
                }
            }

            // Follow field type edges and DI bindings for @Inject fields
            for (FieldNode field : currentClass.fields()) {
                String fieldType = extractTypeFromDescriptor(field.type());
                if (fieldType != null) {
                    addWithReason(fieldType, ReachabilityReason.FIELD_TYPE,
                            current + "." + field.name(), nextDepth, reachabilityMap, workQueue);
                    
                    // For @Inject fields, also follow qualified DI bindings
                    if (QualifierExtractor.isInjectionPoint(field.annotations())) {
                        String qualifier = QualifierExtractor.extractQualifier(field.annotations());
                        Set<String> implementations = graph.getImplementations(fieldType, qualifier);
                        for (String impl : implementations) {
                            addWithReason(impl, ReachabilityReason.DI_BINDING,
                                    current + "." + field.name() + (qualifier != null ? " @" + qualifier : ""),
                                    nextDepth, reachabilityMap, workQueue);
                        }
                    }
                }
            }

            // Follow DI binding edges (Guice, CDI) at class level for non-field bindings
            Set<String> implementations = graph.getImplementations(current);
            for (String impl : implementations) {
                addWithReason(impl, ReachabilityReason.DI_BINDING,
                        current, nextDepth, reachabilityMap, workQueue);
            }
        }

        return new ReachabilityResult(reachabilityMap);
    }

    private void addIfNew(String className, Set<String> reachable, Queue<String> workQueue) {
        if (className != null && !className.startsWith("java.") && !className.startsWith("javax.") &&
                !className.startsWith("sun.") && !className.startsWith("jdk.")) {
            if (reachable.add(className)) {
                workQueue.add(className);
            }
        }
    }

    private void addWithReason(String className, ReachabilityReason reason, String reachedFrom,
            int depth, Map<String, ReachabilityInfo> map, Queue<String> workQueue) {
        if (className != null && !className.startsWith("java.") && !className.startsWith("javax.") &&
                !className.startsWith("sun.") && !className.startsWith("jdk.")) {
            if (!map.containsKey(className)) {
                map.put(className, new ReachabilityInfo(className, reason, reachedFrom, depth));
                workQueue.add(className);
            }
        }
    }

    private boolean isProjectClass(ClassNode cls) {
        if (cls.isProjectClass()) {
            return true;
        }
        for (String prefix : projectPackagePrefixes) {
            if (cls.fqn().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts a class name from a type descriptor.
     * E.g., "Ljava/util/Map;" -> "java.util.Map"
     * E.g., "[Ljava/lang/String;" -> "java.lang.String"
     */
    private String extractTypeFromDescriptor(String descriptor) {
        if (descriptor == null) return null;

        // Skip array dimensions
        int start = 0;
        while (start < descriptor.length() && descriptor.charAt(start) == '[') {
            start++;
        }

        if (start >= descriptor.length()) return null;

        char typeChar = descriptor.charAt(start);
        if (typeChar == 'L') {
            // Object type
            int end = descriptor.indexOf(';', start);
            if (end > start) {
                return descriptor.substring(start + 1, end).replace('/', '.');
            }
        }

        // Primitive type - no class to reference
        return null;
    }

    /**
     * Reason why a class is reachable.
     */
    public enum ReachabilityReason {
        PROJECT_CLASS,   // It's a project class (root)
        EXTENDS,         // Superclass of a reachable class
        IMPLEMENTS,      // Interface implemented by a reachable class
        METHOD_CALL,     // Called by a method in a reachable class
        FIELD_TYPE,      // Type of a field in a reachable class
        ANNOTATION,      // Annotation on a reachable class/method/field
        DI_BINDING       // Implementation bound via DI (Guice, CDI)
    }

    /**
     * Information about why a class is reachable.
     */
    public record ReachabilityInfo(
            String className,
            ReachabilityReason reason,
            String reachedFrom,
            int depth
    ) {}

    /**
     * Result of reachability analysis with path information.
     */
    public record ReachabilityResult(
            Map<String, ReachabilityInfo> reachabilityMap
    ) {
        public Set<String> reachableClasses() {
            return reachabilityMap.keySet();
        }

        public boolean isReachable(String className) {
            return reachabilityMap.containsKey(className);
        }

        public Optional<ReachabilityInfo> getInfo(String className) {
            return Optional.ofNullable(reachabilityMap.get(className));
        }

        public int size() {
            return reachabilityMap.size();
        }
    }
}
