package io.statescan.graph;

import io.statescan.config.LeafTypeConfig;
import io.statescan.model.RiskLevel;

import java.util.*;

/**
 * Finds paths from project roots to leaf types (external state, databases, etc.).
 * <p>
 * This implements Mode 2 (External Ports) of the detection system, complementing
 * the existing Mode 1 (Internal State) detectors.
 * <p>
 * The PathFinder traverses the call graph using BFS, following:
 * <ul>
 *   <li>Field types</li>
 *   <li>Method invocation targets</li>
 *   <li>Inheritance relationships</li>
 *   <li>DI binding edges (critical for Guice/CDI)</li>
 * </ul>
 */
public class PathFinder {

    private final CallGraph graph;
    private final LeafTypeConfig leafConfig;

    /**
     * Internal record for tracking edge information during traversal.
     * <p>
     * For INVOCATION edges: sourceMethod is the calling method, targetMethod is the called method
     * For FIELD edges: sourceMethod is the method accessing the field (or null for class-level)
     * For INHERITANCE/DI_BINDING: both are null (class-level relationships)
     */
    private record EdgeInfo(
            String targetClass,
            String targetMethod,   // For INVOCATION: the specific method being called
            String sourceMethod,   // The method in the source class creating this edge
            EdgeType edgeType
    ) {
        /** Convenience constructor for class-level edges (inheritance, DI) */
        static EdgeInfo classLevel(String targetClass, String label, EdgeType edgeType) {
            return new EdgeInfo(targetClass, null, label, edgeType);
        }
        
        /** Convenience constructor for method invocations */
        static EdgeInfo invocation(String targetClass, String targetMethod, String sourceMethod) {
            return new EdgeInfo(targetClass, targetMethod, sourceMethod, EdgeType.INVOCATION);
        }
        
        /** Convenience constructor for field access */
        static EdgeInfo field(String targetClass, String fieldName, String sourceMethod) {
            return new EdgeInfo(targetClass, null, sourceMethod != null ? sourceMethod : fieldName, EdgeType.FIELD);
        }
    }

    /**
     * Represents a path from a project root to a leaf type.
     * Path now includes method/field information for each step.
     */
    public record StatefulPath(
            String root,           // Project class that starts the path
            List<PathStep> path,   // [root, node1, node2, ..., leaf] with member info
            String leafType,       // The leaf type reached (e.g., "javax.sql.DataSource")
            String leafCategory,   // Category from config (e.g., "externalStateTypes")
            RiskLevel riskLevel
    ) {
        /**
         * Returns a human-readable path representation with method names.
         * Format: "ClassName#method -> ClassName#method -> LeafType"
         */
        public String pathString() {
            return path.stream()
                    .map(PathStep::formatted)
                    .reduce((a, b) -> a + " -> " + b)
                    .orElse("");
        }

        /**
         * Returns just the class names (for backward compatibility).
         */
        public List<String> classNames() {
            return path.stream()
                    .map(PathStep::className)
                    .toList();
        }

        /**
         * Returns the number of hops from root to leaf.
         */
        public int depth() {
            return path.size() - 1;
        }
    }

    public PathFinder(CallGraph graph, LeafTypeConfig leafConfig) {
        this.graph = graph;
        this.leafConfig = leafConfig;
    }

    /**
     * Finds all paths from project roots to leaf types.
     * <p>
     * Only processes roots that are actual project classes (not third-party library classes).
     * Uses a global visited set to avoid duplicate paths and cycles across traversals.
     *
     * @param projectRoots Set of FQNs that are considered project entry points
     * @return List of paths to stateful leaf types
     */
    public List<StatefulPath> findPaths(Set<String> projectRoots) {
        List<StatefulPath> results = new ArrayList<>();
        Set<String> globalVisited = new HashSet<>();

        for (String root : projectRoots) {
            // Skip non-project roots (defensive check)
            if (!isProjectClass(root)) {
                continue;
            }
            bfsFromRoot(root, results, globalVisited);
        }

        return deduplicatePaths(results);
    }

    /**
     * Performs BFS from a single root to find all paths to leaf types.
     * <p>
     * Uses method-level context tracking to ensure accurate call chains.
     * When traversing via INVOCATION, locks to the specific target method
     * and only follows edges from that method.
     *
     * @param root          The project class to start from
     * @param results       List to collect found paths
     * @param globalVisited Shared visited set across all BFS traversals to avoid cycles and duplicates
     */
    private void bfsFromRoot(String root, List<StatefulPath> results, Set<String> globalVisited) {
        // TraversalState now tracks method context for precise call chain tracking
        // methodContext: null = class-level (any method), non-null = specific method only
        record TraversalState(String className, String methodContext, List<PathStep> pathSoFar) {}

        Queue<TraversalState> queue = new LinkedList<>();
        // Track visited (class, method) pairs to handle method-level cycles
        Set<String> localVisited = new HashSet<>();

        // Start with root node (no method context - entry from any method)
        queue.add(new TraversalState(root, null, List.of(PathStep.root(root))));

        while (!queue.isEmpty()) {
            TraversalState state = queue.poll();
            String currentClass = state.className();
            String currentMethod = state.methodContext();

            // Create visit key that includes method context for precise cycle detection
            String visitKey = currentClass + "#" + (currentMethod != null ? currentMethod : "*");
            if (!localVisited.add(visitKey)) {
                continue;
            }

            // Check if current is a leaf type
            String leafCategory = getLeafCategory(currentClass);
            if (leafCategory != null) {
                RiskLevel risk = getRiskLevel(currentClass, leafCategory);
                // For leaf nodes, update the final step to show the target method if available
                List<PathStep> finalPath = state.pathSoFar();
                if (currentMethod != null && !finalPath.isEmpty()) {
                    finalPath = new ArrayList<>(finalPath);
                    PathStep lastStep = finalPath.get(finalPath.size() - 1);
                    // Update leaf node step to show the specific method reached
                    finalPath.set(finalPath.size() - 1, new PathStep(
                            lastStep.className(),
                            currentMethod,
                            lastStep.edgeType()
                    ));
                }
                results.add(new StatefulPath(
                        root,
                        finalPath,
                        currentClass,
                        leafCategory,
                        risk
                ));
                continue;
            }

            // Skip if already globally explored (but allow project classes to be re-explored)
            String globalKey = currentClass + "#" + (currentMethod != null ? currentMethod : "*");
            if (globalVisited.contains(globalKey) && !isProjectClass(currentClass)) {
                continue;
            }
            globalVisited.add(globalKey);

            // Get neighbors with method-aware edge discovery
            for (EdgeInfo edge : getNeighborsWithEdges(currentClass, currentMethod)) {
                String targetVisitKey = edge.targetClass() + "#" + (edge.targetMethod() != null ? edge.targetMethod() : "*");
                if (!localVisited.contains(targetVisitKey) && !isExcludedPackage(edge.targetClass())) {
                    List<PathStep> newPath = new ArrayList<>(state.pathSoFar());

                    // Update previous step to show the SOURCE method that creates this edge
                    if (!newPath.isEmpty()) {
                        PathStep lastStep = newPath.get(newPath.size() - 1);
                        newPath.set(newPath.size() - 1, new PathStep(
                                lastStep.className(),
                                edge.sourceMethod(),
                                edge.edgeType()
                        ));
                    }

                    // Add the target node
                    newPath.add(PathStep.root(edge.targetClass()));

                    // Determine method context for next iteration
                    // INVOCATION: lock to the specific target method
                    // FIELD/INHERITANCE/DI_BINDING: class-level (any method)
                    String nextMethodContext = null;
                    if (edge.edgeType() == EdgeType.INVOCATION) {
                        nextMethodContext = edge.targetMethod();
                    }

                    queue.add(new TraversalState(edge.targetClass(), nextMethodContext, newPath));
                }
            }
        }
    }

    /**
     * Gets all neighbor classes from the current class, with edge information.
     * <p>
     * When methodContext is null (class-level), returns edges from ALL methods.
     * When methodContext is specified, returns ONLY edges from that specific method,
     * enabling accurate method-to-method call chain tracking.
     *
     * @param className     The class to get neighbors for
     * @param methodContext The specific method to get edges from (null for all methods)
     * @return Set of edges to neighbor classes
     */
    private Set<EdgeInfo> getNeighborsWithEdges(String className, String methodContext) {
        Set<EdgeInfo> edges = new HashSet<>();

        Optional<ClassNode> clsOpt = graph.getClass(className);
        if (clsOpt.isEmpty()) {
            return edges;
        }

        ClassNode cls = clsOpt.get();

        // NOTE: We deliberately do NOT add INHERITANCE edges (extends/implements) here.
        // "extends" is a type relationship, not a method call. The actual calls are:
        //   - super() calls → captured as INVOCATION to parent's <init>
        //   - super.method() → captured as INVOCATION to parent's method
        //   - inherited method resolution → handled by "method not found, check superclass" logic below
        // Leaf type detection via inheritance is handled in getLeafCategory() by checking supertypes.

        if (methodContext == null) {
            // CLASS-LEVEL: Get edges from ALL methods
            
            // Field types (class-level view of all fields)
            for (FieldNode field : cls.fields()) {
                String fieldType = extractTypeName(field.type());
                if (fieldType != null) {
                    edges.add(EdgeInfo.field(fieldType, field.name(), null));
                }
            }

            // Method invocations from all methods
            for (MethodNode method : cls.methods()) {
                for (MethodRef inv : method.invocations()) {
                    edges.add(EdgeInfo.invocation(inv.owner(), inv.name(), method.name()));
                }
            }
        } else {
            // METHOD-LEVEL: Only get edges from the specific method
            Set<MethodNode> methods = cls.findMethodsByName(methodContext);
            
            for (MethodNode method : methods) {
                // Method invocations from this specific method only
                for (MethodRef inv : method.invocations()) {
                    edges.add(EdgeInfo.invocation(inv.owner(), inv.name(), method.name()));
                }
                
                // Field accesses from this method (track fields accessed by this method)
                for (FieldRef fieldAccess : method.fieldAccesses()) {
                    String fieldType = extractTypeName(fieldAccess.type());
                    if (fieldType != null && !fieldAccess.owner().equals(className)) {
                        // External field access - add edge to the owning class
                        edges.add(EdgeInfo.field(fieldAccess.owner(), fieldAccess.name(), method.name()));
                    }
                }
            }
            
            // If method not found in this class, it might be inherited - check superclass
            if (methods.isEmpty() && cls.superclass() != null) {
                // The method might be defined in superclass, add edge to explore there
                edges.add(EdgeInfo.invocation(cls.superclass(), methodContext, methodContext));
            }
        }

        // DI BINDING EDGES
        // For each neighbor that's an interface/abstract with DI binding, also add implementations
        Set<String> processedNeighbors = new HashSet<>();
        for (EdgeInfo edge : new HashSet<>(edges)) {
            processedNeighbors.add(edge.targetClass());
            
            // For invocation edges, also add DI bindings for the target class
            // This handles interface dispatch: if calling InterfaceA.method(), 
            // also add edge to ConcreteImpl.method()
            for (String impl : graph.getImplementations(edge.targetClass())) {
                if (!processedNeighbors.contains(impl)) {
                    if (edge.edgeType() == EdgeType.INVOCATION && edge.targetMethod() != null) {
                        // Preserve the target method for interface dispatch
                        edges.add(new EdgeInfo(impl, edge.targetMethod(), "@Inject->" + edge.targetMethod(), EdgeType.DI_BINDING));
                    } else {
                        edges.add(EdgeInfo.classLevel(impl, "@Inject", EdgeType.DI_BINDING));
                    }
                }
            }
        }

        // Also check if the current class itself has DI bindings
        for (String impl : graph.getImplementations(className)) {
            if (!processedNeighbors.contains(impl)) {
                edges.add(EdgeInfo.classLevel(impl, "@Inject", EdgeType.DI_BINDING));
            }
        }

        return edges;
    }

    /**
     * Extracts the class name from a JVM type descriptor.
     * E.g., "Ljavax/sql/DataSource;" -> "javax.sql.DataSource"
     */
    private String extractTypeName(String descriptor) {
        if (descriptor == null) return null;

        // Skip array dimensions
        int start = 0;
        while (start < descriptor.length() && descriptor.charAt(start) == '[') {
            start++;
        }

        if (start >= descriptor.length()) return null;

        if (descriptor.charAt(start) == 'L') {
            int end = descriptor.indexOf(';', start);
            if (end > start) {
                return descriptor.substring(start + 1, end).replace('/', '.');
            }
        }

        return null;
    }

    /**
     * Returns the leaf category if the class is a configured leaf type, null otherwise.
     * Also checks supertypes to handle cases like "class MyCache extends ConcurrentHashMap".
     */
    private String getLeafCategory(String className) {
        // First check the class itself
        String category = getLeafCategoryDirect(className);
        if (category != null) {
            return category;
        }
        
        // Then check supertypes (handles cases like MyThreadLocal extends ThreadLocal)
        for (String supertype : graph.allSupertypes(className)) {
            category = getLeafCategoryDirect(supertype);
            if (category != null) {
                return category;
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a specific class name (not its supertypes) matches a leaf type.
     */
    private String getLeafCategoryDirect(String className) {
        if (leafConfig.isExternalStateType(className)) {
            return "externalStateTypes";
        }
        if (leafConfig.isServiceClientType(className)) {
            return "serviceClientTypes";
        }
        if (leafConfig.isCacheType(className)) {
            return "cacheTypes";
        }
        if (leafConfig.isGrpcType(className)) {
            return "grpcTypes";
        }
        if (leafConfig.isResilienceType(className)) {
            return "resilienceTypes";
        }
        if (leafConfig.isFileStateType(className)) {
            return "fileStateTypes";
        }
        if (leafConfig.isThreadLocalType(className)) {
            return "threadLocalTypes";
        }
        return null;
    }

    /**
     * Determines the risk level for a leaf type.
     */
    private RiskLevel getRiskLevel(String className, String category) {
        return switch (category) {
            // External state (DB, JMS, etc.) and file types are high risk
            case "externalStateTypes", "fileStateTypes" -> RiskLevel.HIGH;
            // ThreadLocal and resilience types are high risk (thread affinity/state issues)
            case "threadLocalTypes", "resilienceTypes" -> RiskLevel.HIGH;
            // HTTP clients and gRPC could cause connection issues
            case "serviceClientTypes", "grpcTypes" -> RiskLevel.MEDIUM;
            // Caches are typically designed to be shared but may cause issues
            case "cacheTypes" -> RiskLevel.MEDIUM;
            default -> RiskLevel.LOW;
        };
    }

    /**
     * Deduplicates paths by removing duplicate or redundant paths.
     * Groups paths by leaf type and keeps unique paths based on their signature.
     */
    private List<StatefulPath> deduplicatePaths(List<StatefulPath> paths) {
        // Use a set to track unique path signatures (root + path + leaf)
        Set<String> seenSignatures = new HashSet<>();
        List<StatefulPath> deduplicated = new ArrayList<>();

        for (StatefulPath path : paths) {
            // Include member names in signature for more accurate deduplication
            String pathStr = path.path().stream()
                    .map(step -> step.className() + (step.memberName() != null ? "#" + step.memberName() : ""))
                    .reduce((a, b) -> a + "->" + b)
                    .orElse("");
            String signature = path.root() + "|" + pathStr + "|" + path.leafType();
            if (seenSignatures.add(signature)) {
                deduplicated.add(path);
            }
        }

        return deduplicated;
    }

    /**
     * Checks if a package should be excluded from traversal.
     * We want to skip JDK internal packages but not javax.* types that are leaf types.
     */
    private boolean isExcludedPackage(String className) {
        // Never exclude leaf types
        if (getLeafCategory(className) != null) {
            return false;
        }

        // Skip JDK internal packages
        return className.startsWith("java.") ||
                className.startsWith("sun.") ||
                className.startsWith("jdk.") ||
                className.startsWith("com.sun.");
    }

    /**
     * Checks if a class is java.lang.Object.
     */
    private boolean isJavaLangObject(String className) {
        return "java.lang.Object".equals(className);
    }

    /**
     * Checks if a class belongs to the project (not a third-party library class).
     */
    private boolean isProjectClass(String className) {
        return graph.getClass(className)
                .map(ClassNode::isProjectClass)
                .orElse(false);
    }
}
