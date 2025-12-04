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
     * Represents a path from a project root to a leaf type.
     */
    public record StatefulPath(
            String root,           // Project class that starts the path
            List<String> path,     // [root, node1, node2, ..., leaf]
            String leafType,       // The leaf type reached (e.g., "javax.sql.DataSource")
            String leafCategory,   // Category from config (e.g., "externalStateTypes")
            RiskLevel riskLevel
    ) {
        /**
         * Returns a human-readable path representation.
         */
        public String pathString() {
            return String.join(" -> ", path);
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
     *
     * @param root          The project class to start from
     * @param results       List to collect found paths
     * @param globalVisited Shared visited set across all BFS traversals to avoid cycles and duplicates
     */
    private void bfsFromRoot(String root, List<StatefulPath> results, Set<String> globalVisited) {
        record TraversalState(String node, List<String> pathSoFar) {}

        Queue<TraversalState> queue = new LinkedList<>();
        Set<String> localVisited = new HashSet<>();

        queue.add(new TraversalState(root, List.of(root)));

        while (!queue.isEmpty()) {
            TraversalState state = queue.poll();
            String current = state.node();

            // Skip if already visited locally (within this BFS)
            if (!localVisited.add(current)) {
                continue;
            }

            // Check if current is a leaf type
            String leafCategory = getLeafCategory(current);
            if (leafCategory != null) {
                RiskLevel risk = getRiskLevel(current, leafCategory);
                results.add(new StatefulPath(
                        root,
                        state.pathSoFar(),
                        current,
                        leafCategory,
                        risk
                ));
                // Don't continue traversal from leaf types
                continue;
            }

            // Skip further traversal if this node was already fully explored by another root
            // This prevents duplicate sub-paths when multiple project classes reach the same library code
            if (globalVisited.contains(current) && !isProjectClass(current)) {
                continue;
            }
            globalVisited.add(current);

            // Get neighbors via all edge types
            for (String neighbor : getNeighbors(current)) {
                if (!localVisited.contains(neighbor) && !isExcludedPackage(neighbor)) {
                    List<String> newPath = new ArrayList<>(state.pathSoFar());
                    newPath.add(neighbor);
                    queue.add(new TraversalState(neighbor, newPath));
                }
            }
        }
    }

    /**
     * Gets all neighbor classes from the current class.
     * Follows field types, method invocations, inheritance, and DI bindings.
     */
    private Set<String> getNeighbors(String className) {
        Set<String> neighbors = new HashSet<>();

        Optional<ClassNode> clsOpt = graph.getClass(className);
        if (clsOpt.isEmpty()) {
            return neighbors;
        }

        ClassNode cls = clsOpt.get();

        // 1. Superclass
        if (cls.superclass() != null && !isJavaLangObject(cls.superclass())) {
            neighbors.add(cls.superclass());
        }

        // 2. Interfaces
        neighbors.addAll(cls.interfaces());

        // 3. Field types
        for (FieldNode field : cls.fields()) {
            String fieldType = extractTypeName(field.type());
            if (fieldType != null) {
                neighbors.add(fieldType);
            }
        }

        // 4. Method invocation targets
        for (MethodNode method : cls.methods()) {
            for (MethodRef inv : method.invocations()) {
                neighbors.add(inv.owner());
            }
        }

        // 5. DI BINDING EDGES (critical addition!)
        // For each neighbor that's an interface with DI binding, also add implementations
        Set<String> boundImpls = new HashSet<>();
        for (String neighbor : neighbors) {
            boundImpls.addAll(graph.getImplementations(neighbor));
        }
        neighbors.addAll(boundImpls);

        // Also check if the current class itself has DI bindings
        neighbors.addAll(graph.getImplementations(className));

        return neighbors;
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
     */
    private String getLeafCategory(String className) {
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
            String signature = path.root() + "|" + String.join("->", path.path()) + "|" + path.leafType();
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
