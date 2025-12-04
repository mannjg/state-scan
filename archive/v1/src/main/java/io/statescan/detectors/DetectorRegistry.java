package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.CallGraph;
import io.statescan.model.Finding;

import java.util.*;

/**
 * Registry of all available detectors.
 * Manages detector execution and result aggregation.
 */
public class DetectorRegistry {

    private final List<Detector> detectors;

    private DetectorRegistry(List<Detector> detectors) {
        this.detectors = List.copyOf(detectors);
    }

    /**
     * Creates a registry with all default detectors.
     */
    public static DetectorRegistry createDefault() {
        return new DetectorRegistry(List.of(
                new StaticStateDetector(),
                new SingletonDetector(),
                new MutableFieldDetector(),
                new CacheDetector(),
                new ThreadLocalDetector(),
                new ExternalStateDetector(),
                new ServiceClientDetector(),
                new ResilienceStateDetector(),
                new FileStateDetector()
        ));
    }

    /**
     * Creates a registry with specific detectors.
     */
    public static DetectorRegistry of(Detector... detectors) {
        return new DetectorRegistry(Arrays.asList(detectors));
    }

    /**
     * Runs all enabled detectors and returns aggregated findings.
     *
     * @param graph            The call graph to analyze
     * @param config           The leaf type configuration
     * @param reachableClasses Set of FQNs reachable from project roots (for filtering)
     * @return All findings from all detectors
     */
    public List<Finding> runAll(CallGraph graph, LeafTypeConfig config, Set<String> reachableClasses) {
        List<Finding> allFindings = new ArrayList<>();
        for (Detector detector : detectors) {
            if (detector.enabledByDefault()) {
                List<Finding> findings = detector.detect(graph, config, reachableClasses);
                allFindings.addAll(findings);
            }
        }
        return allFindings;
    }

    /**
     * Runs specific detectors by ID.
     *
     * @param graph            The call graph to analyze
     * @param config           The leaf type configuration
     * @param reachableClasses Set of FQNs reachable from project roots (for filtering)
     * @param detectorIds      IDs of detectors to run
     * @return All findings from specified detectors
     */
    public List<Finding> run(CallGraph graph, LeafTypeConfig config, Set<String> reachableClasses, Set<String> detectorIds) {
        List<Finding> allFindings = new ArrayList<>();
        for (Detector detector : detectors) {
            if (detectorIds.contains(detector.id())) {
                List<Finding> findings = detector.detect(graph, config, reachableClasses);
                allFindings.addAll(findings);
            }
        }
        return allFindings;
    }

    /**
     * Returns all registered detectors.
     */
    public List<Detector> allDetectors() {
        return detectors;
    }

    /**
     * Returns a detector by ID, if present.
     */
    public Optional<Detector> getById(String id) {
        return detectors.stream()
                .filter(d -> d.id().equals(id))
                .findFirst();
    }
}
