package io.statescan.model;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Container for the complete scan result.
 *
 * @param classes Map of class FQN to ClassInfo
 */
public record ScanResult(
    Map<String, ClassInfo> classes
) {
    /**
     * Create a ScanResult with an immutable copy of the classes map.
     */
    public ScanResult {
        classes = Map.copyOf(classes);
    }

    /**
     * Get a class by FQN.
     */
    public Optional<ClassInfo> getClass(String fqn) {
        return Optional.ofNullable(classes.get(fqn));
    }

    /**
     * Stream all classes.
     */
    public Stream<ClassInfo> allClasses() {
        return classes.values().stream();
    }

    /**
     * Returns the number of classes scanned.
     */
    public int classCount() {
        return classes.size();
    }

    /**
     * Returns the total number of methods across all classes.
     */
    public int methodCount() {
        return classes.values().stream()
            .mapToInt(c -> c.methods().size())
            .sum();
    }

    /**
     * Returns the total number of actors found across all methods.
     */
    public int actorCount() {
        return classes.values().stream()
            .flatMap(c -> c.methods().values().stream())
            .mapToInt(m -> m.actors().size())
            .sum();
    }
}
