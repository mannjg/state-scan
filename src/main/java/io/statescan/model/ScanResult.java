package io.statescan.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
     * Builds a map of interface/abstract class FQN to the set of concrete implementing class FQNs.
     * This includes both direct implementations and transitive implementations through inheritance.
     */
    public Map<String, Set<String>> buildImplementationMap() {
        Map<String, Set<String>> implMap = new HashMap<>();
        
        // For each concrete class, register it as an implementation of its interfaces and abstract superclasses
        for (ClassInfo classInfo : classes.values()) {
            if (!classInfo.isConcrete()) {
                continue;
            }
            
            // Register for directly implemented interfaces
            for (String iface : classInfo.implementedInterfaces()) {
                implMap.computeIfAbsent(iface, k -> new HashSet<>()).add(classInfo.fqn());
            }
            
            // Walk the superclass chain to find abstract classes
            String current = classInfo.superClass();
            while (current != null) {
                ClassInfo superInfo = classes.get(current);
                if (superInfo != null) {
                    if (superInfo.isAbstract()) {
                        implMap.computeIfAbsent(current, k -> new HashSet<>()).add(classInfo.fqn());
                    }
                    // Also inherit interfaces from superclass
                    for (String iface : superInfo.implementedInterfaces()) {
                        implMap.computeIfAbsent(iface, k -> new HashSet<>()).add(classInfo.fqn());
                    }
                    current = superInfo.superClass();
                } else {
                    break;
                }
            }
        }
        
        return implMap;
    }

    /**
     * Finds concrete implementations of the given type (interface or abstract class).
     * 
     * @param typeFqn The fully qualified name of the interface or abstract class
     * @return Set of concrete implementation FQNs, empty if none found
     */
    public Set<String> findImplementations(String typeFqn) {
        return buildImplementationMap().getOrDefault(typeFqn, Set.of());
    }

    /**
     * Checks if the given type is an interface or abstract class that needs resolution.
     */
    public boolean needsResolution(String typeFqn) {
        ClassInfo info = classes.get(typeFqn);
        return info != null && info.needsResolution();
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
