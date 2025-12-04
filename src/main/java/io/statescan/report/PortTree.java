package io.statescan.report;

import io.statescan.graph.CallGraph;
import io.statescan.graph.ClassNode;
import io.statescan.model.Finding;
import io.statescan.util.TypeUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Organizes path-based findings into a tree structure for visualization.
 * Groups by category -> leaf type -> paths.
 * <p>
 * This is used to display the "EXTERNAL PORTS" section showing how
 * project code connects to external dependencies like databases, caches, HTTP clients, etc.
 */
public class PortTree {

    // Human-readable category names for display
    private static final Map<String, String> CATEGORY_DISPLAY_NAMES = Map.of(
            "externalStateTypes", "DATABASE",
            "serviceClientTypes", "HTTP/SERVICE CLIENTS",
            "cacheTypes", "CACHE",
            "grpcTypes", "GRPC",
            "resilienceTypes", "RESILIENCE",
            "fileStateTypes", "FILE SYSTEM",
            "threadLocalTypes", "THREAD LOCAL"
    );

    /**
     * A category of external ports (e.g., DATABASE, CACHE).
     *
     * @param categoryId   Internal category ID from leaf-types.yaml
     * @param displayName  Human-readable category name
     * @param leafTypes    List of leaf types in this category
     * @param totalPaths   Total number of paths in this category
     */
    public record CategoryNode(
            String categoryId,
            String displayName,
            List<LeafTypeNode> leafTypes,
            int totalPaths
    ) {}

    /**
     * A specific external type within a category (e.g., javax.sql.DataSource).
     *
     * @param leafType       Fully qualified type name
     * @param simpleLeafType Simple type name for display
     * @param paths          List of paths from project code to this type
     */
    public record LeafTypeNode(
            String leafType,
            String simpleLeafType,
            List<PathEntry> paths
    ) {}

    /**
     * A single path from project code to an external type.
     *
     * @param rootClass   The project class where the path starts
     * @param path        Full path as list of class names
     * @param pathString  Formatted path string for display
     */
    public record PathEntry(
            String rootClass,
            List<String> path,
            String pathString
    ) {}

    /**
     * Builds a tree structure from path-based findings.
     * <p>
     * Findings are expected to have:
     * - pattern: "Path to {categoryId}" (e.g., "Path to externalStateTypes")
     * - className: The leaf type reached (e.g., "javax.sql.DataSource")
     * - reachabilityPath: List of class names from root to leaf
     * - rootClass: The project class where the path starts
     *
     * @param pathFindings List of findings with reachability paths
     * @param graph        CallGraph for determining project vs third-party classes (for FQCN display)
     * @return List of category nodes forming the tree
     */
    public static List<CategoryNode> buildTree(List<Finding> pathFindings, CallGraph graph) {
        // Group by category (extracted from pattern "Path to XXX")
        Map<String, List<Finding>> byCategory = pathFindings.stream()
                .filter(f -> f.pattern() != null && f.pattern().startsWith("Path to "))
                .collect(Collectors.groupingBy(f ->
                        f.pattern().substring("Path to ".length())));

        List<CategoryNode> categories = new ArrayList<>();

        for (Map.Entry<String, List<Finding>> categoryEntry : byCategory.entrySet()) {
            String categoryId = categoryEntry.getKey();
            List<Finding> findings = categoryEntry.getValue();

            // Group within category by leaf type (className)
            Map<String, List<Finding>> byLeafType = findings.stream()
                    .collect(Collectors.groupingBy(Finding::className));

            List<LeafTypeNode> leafTypes = new ArrayList<>();
            for (Map.Entry<String, List<Finding>> leafEntry : byLeafType.entrySet()) {
                String leafType = TypeUtils.cleanTypeName(leafEntry.getKey());

                List<PathEntry> paths = leafEntry.getValue().stream()
                        .map(f -> new PathEntry(
                                f.rootClass(),
                                f.reachabilityPath(),
                                formatPath(f.reachabilityPath(), graph)
                        ))
                        .toList();

                leafTypes.add(new LeafTypeNode(
                        leafType,
                        TypeUtils.simpleClassName(leafType),
                        paths
                ));
            }

            // Sort leaf types alphabetically by simple name
            leafTypes.sort(Comparator.comparing(LeafTypeNode::simpleLeafType));

            String displayName = CATEGORY_DISPLAY_NAMES.getOrDefault(
                    categoryId,
                    categoryId.toUpperCase().replace("TYPES", "")
            );

            categories.add(new CategoryNode(
                    categoryId,
                    displayName,
                    leafTypes,
                    findings.size()
            ));
        }

        // Sort categories by path count descending
        categories.sort((a, b) -> Integer.compare(b.totalPaths(), a.totalPaths()));

        return categories;
    }

    /**
     * Formats a path as a string of class names joined by arrows.
     * <p>
     * Uses hybrid formatting:
     * - Project classes: simple name (user knows their code)
     * - Third-party classes: FQCN (helps identify which library)
     * <p>
     * E.g., ["com.example.MyService", "org.apache.commons.pool.PooledConnection", "javax.sql.DataSource"]
     * becomes "MyService -> org.apache.commons.pool.PooledConnection -> javax.sql.DataSource"
     *
     * @param path  List of fully qualified class names
     * @param graph CallGraph for determining project vs third-party classes
     */
    private static String formatPath(List<String> path, CallGraph graph) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.stream()
                .map(fqn -> formatClassName(fqn, graph))
                .collect(Collectors.joining(" -> "));
    }

    /**
     * Formats a class name for display.
     * Project classes use simple names, third-party classes use FQCNs.
     */
    private static String formatClassName(String fqn, CallGraph graph) {
        Optional<ClassNode> cls = graph.getClass(fqn);
        if (cls.isPresent() && cls.get().isProjectClass()) {
            return TypeUtils.simpleClassName(fqn);
        }
        // Third-party class: show FQCN to help identify the library
        return fqn;
    }
}
