package io.statescan.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents type narrowing context for a method invocation.
 * Tracks how parameter types are narrowed based on the call path.
 *
 * @param methodRef       The method this context applies to
 * @param parameterTypes  Map of parameter index to narrowed type(s)
 * @param callPath        The call path leading to this narrowing (list of method keys)
 */
public record TypeContext(
    MethodRef methodRef,
    Map<Integer, Set<String>> parameterTypes,
    List<String> callPath
) {
    public TypeContext {
        // Deep copy for immutability
        parameterTypes = parameterTypes.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e -> Set.copyOf(e.getValue())
            ));
        callPath = List.copyOf(callPath);
    }

    /**
     * Get the narrowed types for a specific parameter.
     */
    public Set<String> getNarrowedTypes(int paramIndex) {
        return parameterTypes.getOrDefault(paramIndex, Set.of());
    }

    /**
     * Check if this context has any narrowing for the given parameter.
     */
    public boolean hasNarrowing(int paramIndex) {
        return parameterTypes.containsKey(paramIndex);
    }

    /**
     * Get the immediate caller (last element in call path).
     */
    public String immediateCaller() {
        return callPath.isEmpty() ? null : callPath.get(callPath.size() - 1);
    }

    /**
     * Get the entry point (first element in call path).
     */
    public String entryPoint() {
        return callPath.isEmpty() ? null : callPath.get(0);
    }

    /**
     * Get the call path depth.
     */
    public int depth() {
        return callPath.size();
    }
}
