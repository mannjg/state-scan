package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.CallGraph;
import io.statescan.model.Finding;

import java.util.List;
import java.util.Set;

/**
 * Base interface for all state detectors.
 * Each detector analyzes the call graph for a specific type of stateful pattern.
 */
public interface Detector {

    /**
     * Returns a unique identifier for this detector.
     */
    String id();

    /**
     * Returns a human-readable description of what this detector finds.
     */
    String description();

    /**
     * Detects stateful patterns in the given call graph.
     *
     * @param graph            The (possibly filtered) call graph to analyze
     * @param config           The leaf type configuration
     * @param reachableClasses Set of fully-qualified class names that are reachable from project roots.
     *                         Detectors should only analyze classes in this set (instead of using isProjectClass).
     *                         This enables detection of issues in intermediate libraries reachable via DI.
     * @return List of findings from this detector
     */
    List<Finding> detect(CallGraph graph, LeafTypeConfig config, Set<String> reachableClasses);

    /**
     * Returns true if this detector is enabled by default.
     */
    default boolean enabledByDefault() {
        return true;
    }
}
