package io.statescan.model;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The complete callgraph built from scan results.
 */
public record CallGraph(
    Map<String, Set<CallEdge>> outgoingEdges,  // caller key -> edges
    Map<String, Set<CallEdge>> incomingEdges,  // callee key -> edges
    Map<String, List<TypeContext>> typeContexts, // method key -> narrowed contexts
    Set<MethodRef> rootMethods,   // entry points (no incoming calls)
    Set<MethodRef> leafMethods    // terminal points (no outgoing calls or only external)
) {
    public CallGraph {
        outgoingEdges = deepCopyEdgeMap(outgoingEdges);
        incomingEdges = deepCopyEdgeMap(incomingEdges);
        typeContexts = deepCopyContextMap(typeContexts);
        rootMethods = Set.copyOf(rootMethods);
        leafMethods = Set.copyOf(leafMethods);
    }

    private static Map<String, Set<CallEdge>> deepCopyEdgeMap(Map<String, Set<CallEdge>> map) {
        return map.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e -> Set.copyOf(e.getValue())
            ));
    }

    private static Map<String, List<TypeContext>> deepCopyContextMap(Map<String, List<TypeContext>> map) {
        return map.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e -> List.copyOf(e.getValue())
            ));
    }

    /**
     * Get all methods called by the given method.
     */
    public Set<CallEdge> getCallees(MethodRef caller) {
        return outgoingEdges.getOrDefault(caller.key(), Set.of());
    }

    /**
     * Get all methods that call the given method.
     */
    public Set<CallEdge> getCallers(MethodRef callee) {
        return incomingEdges.getOrDefault(callee.key(), Set.of());
    }

    /**
     * Get type contexts for a method (different narrowings per call path).
     */
    public List<TypeContext> getTypeContexts(MethodRef method) {
        return typeContexts.getOrDefault(method.key(), List.of());
    }

    /**
     * Stream all edges.
     */
    public Stream<CallEdge> allEdges() {
        return outgoingEdges.values().stream().flatMap(Set::stream);
    }

    /**
     * Get the total number of edges.
     */
    public int edgeCount() {
        return outgoingEdges.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Get the number of unique methods in the graph.
     */
    public int methodCount() {
        Set<String> methods = new HashSet<>();
        methods.addAll(outgoingEdges.keySet());
        methods.addAll(incomingEdges.keySet());
        return methods.size();
    }

    /**
     * Check if a method is a root (entry point).
     */
    public boolean isRoot(MethodRef method) {
        return rootMethods.contains(method);
    }

    /**
     * Check if a method is a leaf (terminal point).
     */
    public boolean isLeaf(MethodRef method) {
        return leafMethods.contains(method);
    }

    /**
     * Get all methods that are both roots and leaves (isolated methods).
     */
    public Set<MethodRef> getIsolatedMethods() {
        Set<MethodRef> isolated = new HashSet<>(rootMethods);
        isolated.retainAll(leafMethods);
        return isolated;
    }

    /**
     * Create an empty callgraph.
     */
    public static CallGraph empty() {
        return new CallGraph(Map.of(), Map.of(), Map.of(), Set.of(), Set.of());
    }
}
