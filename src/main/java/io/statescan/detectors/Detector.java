package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.CallGraph;
import io.statescan.model.Finding;

import java.util.List;

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
     * @param graph  The (possibly filtered) call graph to analyze
     * @param config The leaf type configuration
     * @return List of findings from this detector
     */
    List<Finding> detect(CallGraph graph, LeafTypeConfig config);

    /**
     * Returns true if this detector is enabled by default.
     */
    default boolean enabledByDefault() {
        return true;
    }
}
