package io.statescan.analysis;

import io.statescan.bytecode.DescriptorParser;
import io.statescan.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes context-sensitive type narrowings.
 * <p>
 * Key insight: When a parameter declared as InterfaceType is passed to a method
 * expecting ConcreteType, we can narrow the parameter's type in that context.
 * <p>
 * Context-sensitivity: The same method may have different type narrowings
 * depending on the call path leading to it.
 */
public class TypeNarrower {
    private final ScanResult scanResult;
    private final Set<String> rootPackages;
    private final Map<String, Set<CallEdge>> outgoingEdges;
    private final Map<String, Set<CallEdge>> incomingEdges;
    private final Map<String, Set<String>> implementationMap;

    // Cache: method key -> parameter index -> declared type
    private final Map<String, List<String>> methodParameterTypes = new HashMap<>();

    public TypeNarrower(ScanResult scanResult,
                        Set<String> rootPackages,
                        Map<String, Set<CallEdge>> outgoing,
                        Map<String, Set<CallEdge>> incoming) {
        this.scanResult = scanResult;
        this.rootPackages = rootPackages;
        this.outgoingEdges = outgoing;
        this.incomingEdges = incoming;
        this.implementationMap = scanResult.buildImplementationMap();
        buildParameterTypeCache();
    }

    private void buildParameterTypeCache() {
        for (ClassInfo classInfo : scanResult.classes().values()) {
            for (MethodInfo methodInfo : classInfo.methods().values()) {
                String key = classInfo.fqn() + "#" + methodInfo.name() + methodInfo.descriptor();
                List<String> paramTypes = DescriptorParser.parseParameterTypes(methodInfo.descriptor());
                methodParameterTypes.put(key, paramTypes);
            }
        }
    }

    /**
     * Compute type contexts for all methods.
     */
    public Map<String, List<TypeContext>> computeContexts() {
        Map<String, List<TypeContext>> result = new HashMap<>();

        // For each method that receives calls
        for (Map.Entry<String, Set<CallEdge>> entry : incomingEdges.entrySet()) {
            String calleeKey = entry.getKey();
            Set<CallEdge> callers = entry.getValue();

            // Only process methods in root packages
            if (!isCalleeInRootPackages(calleeKey)) {
                continue;
            }

            List<TypeContext> contexts = computeContextsForMethod(calleeKey, callers);
            if (!contexts.isEmpty()) {
                result.put(calleeKey, contexts);
            }
        }

        return result;
    }

    private List<TypeContext> computeContextsForMethod(String methodKey, Set<CallEdge> inEdges) {
        List<TypeContext> contexts = new ArrayList<>();

        // Get declared parameter types for this method
        List<String> declaredTypes = methodParameterTypes.get(methodKey);
        if (declaredTypes == null || declaredTypes.isEmpty()) {
            return contexts;
        }

        // Group edges by their narrowing effect
        Map<Map<Integer, Set<String>>, List<CallEdge>> narrowingGroups = new LinkedHashMap<>();

        for (CallEdge edge : inEdges) {
            // Only process edges from root packages
            if (!isInRootPackages(edge.caller().classFqn())) {
                continue;
            }

            Map<Integer, Set<String>> narrowing = computeNarrowing(edge, declaredTypes);
            if (!narrowing.isEmpty()) {
                narrowingGroups.computeIfAbsent(narrowing, k -> new ArrayList<>()).add(edge);
            }
        }

        // Create a TypeContext for each unique narrowing
        for (Map.Entry<Map<Integer, Set<String>>, List<CallEdge>> group : narrowingGroups.entrySet()) {
            Map<Integer, Set<String>> narrowing = group.getKey();
            List<CallEdge> edges = group.getValue();

            // Build call paths from the edges
            for (CallEdge edge : edges) {
                List<String> callPath = buildCallPath(edge);
                contexts.add(new TypeContext(edge.callee(), narrowing, callPath));
            }
        }

        return contexts;
    }

    /**
     * Compute type narrowing for a single call edge.
     */
    private Map<Integer, Set<String>> computeNarrowing(CallEdge edge, List<String> declaredTypes) {
        Map<Integer, Set<String>> narrowing = new LinkedHashMap<>();

        for (Map.Entry<Integer, ArgumentRef> paramEntry : edge.parameterFlow().entrySet()) {
            int paramIndex = paramEntry.getKey();
            ArgumentRef argRef = paramEntry.getValue();

            if (paramIndex >= declaredTypes.size()) continue;

            String declaredType = declaredTypes.get(paramIndex);
            String argumentType = argRef.typeFqn();

            // Skip primitives and nulls
            if (argumentType == null || isPrimitive(argumentType) || "null".equals(argumentType)) {
                continue;
            }

            // Check if argument type is more specific than declared type
            if (isNarrowing(declaredType, argumentType)) {
                narrowing.put(paramIndex, Set.of(argumentType));
            }
        }

        return narrowing;
    }

    /**
     * Check if argumentType is a narrowing of declaredType.
     * True if declaredType is interface/abstract and argumentType implements/extends it.
     */
    private boolean isNarrowing(String declaredType, String argumentType) {
        if (declaredType == null || argumentType == null) {
            return false;
        }
        if (declaredType.equals(argumentType)) {
            return false;  // Same type, no narrowing
        }

        // Check if declared type is interface/abstract
        ClassInfo declaredInfo = scanResult.classes().get(declaredType);
        if (declaredInfo == null || !declaredInfo.needsResolution()) {
            return false;  // Not an interface/abstract, no narrowing possible
        }

        // Check if argument type implements/extends declared type
        Set<String> implementations = implementationMap.get(declaredType);
        if (implementations != null && implementations.contains(argumentType)) {
            return true;
        }

        // Also check direct type hierarchy
        ClassInfo argInfo = scanResult.classes().get(argumentType);
        if (argInfo != null) {
            if (argInfo.implementedInterfaces().contains(declaredType)) {
                return true;
            }
            // Walk superclass chain
            String superClass = argInfo.superClass();
            while (superClass != null) {
                if (superClass.equals(declaredType)) {
                    return true;
                }
                ClassInfo superInfo = scanResult.classes().get(superClass);
                if (superInfo != null) {
                    if (superInfo.implementedInterfaces().contains(declaredType)) {
                        return true;
                    }
                    superClass = superInfo.superClass();
                } else {
                    break;
                }
            }
        }

        return false;
    }

    /**
     * Build the call path leading to this edge.
     * Includes the immediate caller and potentially deeper callers.
     */
    private List<String> buildCallPath(CallEdge edge) {
        List<String> path = new ArrayList<>();
        path.add(edge.caller().key());

        // Optionally extend with deeper call paths
        // For now, just track immediate caller to avoid explosion
        // Could be extended to track full paths through the graph

        return path;
    }

    /**
     * Check if a class FQN is within the root packages.
     */
    private boolean isInRootPackages(String classFqn) {
        if (rootPackages.isEmpty()) {
            return scanResult.classes().containsKey(classFqn);
        }
        return rootPackages.stream()
            .anyMatch(pkg -> classFqn.startsWith(pkg + ".") || classFqn.equals(pkg));
    }

    /**
     * Check if a method key's class is in root packages.
     */
    private boolean isCalleeInRootPackages(String methodKey) {
        // Method key format: "class.fqn#methodName(descriptor)"
        int hashIdx = methodKey.indexOf('#');
        if (hashIdx > 0) {
            String classFqn = methodKey.substring(0, hashIdx);
            return isInRootPackages(classFqn);
        }
        return false;
    }

    private boolean isPrimitive(String type) {
        return type != null && (
            type.equals("int") || type.equals("long") || type.equals("short") ||
            type.equals("byte") || type.equals("char") || type.equals("boolean") ||
            type.equals("float") || type.equals("double") || type.equals("void") ||
            type.equals("primitive") || type.equals("element")
        );
    }
}
