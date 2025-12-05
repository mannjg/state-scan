package io.statescan.analysis;

import io.statescan.ScanConfig;
import io.statescan.bytecode.DescriptorParser;
import io.statescan.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a callgraph from scan results.
 * <p>
 * The callgraph captures:
 * - Caller -> Callee edges between methods
 * - Parameter flow: which arguments are passed to each parameter
 * - Root methods: entry points with no incoming calls
 * - Leaf methods: terminal points with no outgoing calls (or only external calls)
 */
public class CallGraphBuilder {
    private final ScanResult scanResult;
    private final Set<String> rootPackages;
    private final Map<String, Set<String>> implementationMap;
    private final ScanConfig config;  // May be null for backward compatibility

    /**
     * Create a CallGraphBuilder.
     *
     * @param scanResult   The scan results containing all classes
     * @param rootPackages Package prefixes to consider "internal" (for root/leaf detection)
     */
    public CallGraphBuilder(ScanResult scanResult, Set<String> rootPackages) {
        this(scanResult, rootPackages, null);
    }

    /**
     * Create a CallGraphBuilder with config for class exclusions.
     *
     * @param scanResult   The scan results containing all classes
     * @param rootPackages Package prefixes to consider "internal" (for root/leaf detection)
     * @param config       Optional scan config with class exclusions for callgraph pruning
     */
    public CallGraphBuilder(ScanResult scanResult, Set<String> rootPackages, ScanConfig config) {
        this.scanResult = scanResult;
        this.rootPackages = rootPackages;
        this.implementationMap = scanResult.buildImplementationMap();
        this.config = config;
    }

    /**
     * Build the complete callgraph.
     */
    public CallGraph build() {
        Map<String, Set<CallEdge>> outgoing = new HashMap<>();
        Map<String, Set<CallEdge>> incoming = new HashMap<>();
        Map<String, Integer> invocationCounts = new HashMap<>();  // For invocation indices

        // Collect all methods in root packages
        Set<MethodRef> allInternalMethods = new HashSet<>();

        // Phase 1: Build edges from invocations
        for (ClassInfo classInfo : scanResult.classes().values()) {
            boolean classInRootPackages = isInRootPackages(classInfo.fqn());

            for (MethodInfo methodInfo : classInfo.methods().values()) {
                MethodRef caller = MethodRef.of(classInfo, methodInfo);

                if (classInRootPackages) {
                    allInternalMethods.add(caller);
                }

                for (MethodInvocation inv : methodInfo.invocations()) {
                    // Skip invocations to excluded classes
                    if (config != null && config.isClassExcludedFromCallgraph(inv.targetClassFqn())) {
                        continue;
                    }

                    // Skip invocations to excluded methods
                    if (config != null && config.isMethodExcludedFromCallgraph(inv.targetClassFqn(), inv.methodName())) {
                        continue;
                    }

                    // Resolve target method(s)
                    List<MethodRef> targets = resolveTargets(inv);

                    for (MethodRef target : targets) {
                        // Build parameter flow map
                        Map<Integer, ArgumentRef> paramFlow = new HashMap<>();
                        for (int i = 0; i < inv.arguments().size(); i++) {
                            paramFlow.put(i, inv.arguments().get(i));
                        }

                        // Get invocation index for this caller->target pair
                        String pairKey = caller.key() + "->" + target.key();
                        int idx = invocationCounts.merge(pairKey, 1, (old, v) -> old + 1) - 1;

                        CallEdge edge = new CallEdge(caller, target, inv.invokeType(), inv.receiver(), paramFlow, idx);

                        outgoing.computeIfAbsent(caller.key(), k -> new LinkedHashSet<>()).add(edge);
                        incoming.computeIfAbsent(target.key(), k -> new LinkedHashSet<>()).add(edge);
                    }
                }
            }
        }

        // Phase 2: Find root and leaf methods
        Set<MethodRef> rootMethods = findRootMethods(allInternalMethods, outgoing, incoming);
        Set<MethodRef> leafMethods = findLeafMethods(allInternalMethods, outgoing);

        // Phase 3: Compute type contexts
        TypeNarrower narrower = new TypeNarrower(scanResult, rootPackages, outgoing, incoming);
        Map<String, List<TypeContext>> typeContexts = narrower.computeContexts();

        return new CallGraph(outgoing, incoming, typeContexts, rootMethods, leafMethods);
    }

    /**
     * Resolve invocation target to actual method(s).
     * For interface/abstract calls, may return multiple implementations.
     */
    private List<MethodRef> resolveTargets(MethodInvocation inv) {
        String targetClass = inv.targetClassFqn();

        // Check if target is interface/abstract needing resolution
        ClassInfo targetInfo = scanResult.classes().get(targetClass);

        if (targetInfo != null && targetInfo.needsResolution() &&
            inv.invokeType() != InvokeType.STATIC) {

            Set<String> impls = implementationMap.get(targetClass);
            if (impls != null && !impls.isEmpty()) {
                // Return all implementations as potential targets
                List<MethodRef> targets = new ArrayList<>();
                for (String implFqn : impls) {
                    // Only add if the implementation actually has this method
                    ClassInfo implInfo = scanResult.classes().get(implFqn);
                    if (implInfo != null) {
                        String methodKey = inv.methodName() + inv.methodDescriptor();
                        if (implInfo.methods().containsKey(methodKey)) {
                            targets.add(new MethodRef(implFqn, inv.methodName(), inv.methodDescriptor()));
                        }
                    }
                }
                if (!targets.isEmpty()) {
                    return targets;
                }
            }
        }

        // Single target (concrete class or unresolvable)
        return List.of(new MethodRef(targetClass, inv.methodName(), inv.methodDescriptor()));
    }

    /**
     * Find root methods - entry points with no incoming calls from internal methods.
     * Only considers methods that actually participate in the callgraph.
     */
    private Set<MethodRef> findRootMethods(Set<MethodRef> allInternalMethods,
                                            Map<String, Set<CallEdge>> outgoing,
                                            Map<String, Set<CallEdge>> incoming) {
        // Only consider methods that are actually part of the callgraph
        Set<String> methodsInGraph = new HashSet<>();
        methodsInGraph.addAll(outgoing.keySet());
        methodsInGraph.addAll(incoming.keySet());

        return allInternalMethods.stream()
            .filter(m -> methodsInGraph.contains(m.key()))
            // Filter out methods from excluded classes/packages
            .filter(m -> config == null || !config.isClassExcludedFromCallgraph(m.classFqn()))
            // Filter out excluded methods
            .filter(m -> config == null || !config.isMethodExcludedFromCallgraph(m.classFqn(), m.methodName()))
            .filter(m -> {
                Set<CallEdge> callers = incoming.get(m.key());
                if (callers == null || callers.isEmpty()) {
                    return true; // No callers at all
                }
                // Check if all callers are external
                return callers.stream()
                    .allMatch(edge -> !isInRootPackages(edge.caller().classFqn()));
            })
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Find leaf methods - methods with no outgoing calls to internal methods.
     * Also treats calls to excluded classes as external (for callgraph pruning).
     */
    private Set<MethodRef> findLeafMethods(Set<MethodRef> allInternalMethods,
                                            Map<String, Set<CallEdge>> outgoing) {
        return allInternalMethods.stream()
            // Filter out methods from excluded classes/packages
            .filter(m -> config == null || !config.isClassExcludedFromCallgraph(m.classFqn()))
            // Filter out excluded methods
            .filter(m -> config == null || !config.isMethodExcludedFromCallgraph(m.classFqn(), m.methodName()))
            .filter(m -> {
                Set<CallEdge> callees = outgoing.get(m.key());
                if (callees == null || callees.isEmpty()) {
                    return true; // No outgoing calls
                }
                // Check if all callees are external or excluded
                return callees.stream()
                    .allMatch(edge -> isExternalOrExcluded(edge.callee()));
            })
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Check if a method is external (not in root packages) or excluded from callgraph.
     */
    private boolean isExternalOrExcluded(MethodRef method) {
        String classFqn = method.classFqn();
        // First check if it's excluded from callgraph (class or method)
        if (config != null && config.isClassExcludedFromCallgraph(classFqn)) {
            return true;
        }
        if (config != null && config.isMethodExcludedFromCallgraph(classFqn, method.methodName())) {
            return true;
        }
        // Then check if it's external
        return !isInRootPackages(classFqn);
    }

    /**
     * Check if a class FQN is within the root packages.
     */
    private boolean isInRootPackages(String classFqn) {
        if (rootPackages.isEmpty()) {
            // If no root packages specified, consider all scanned classes as internal
            return scanResult.classes().containsKey(classFqn);
        }
        return rootPackages.stream()
            .anyMatch(pkg -> classFqn.startsWith(pkg + ".") || classFqn.equals(pkg));
    }
}
