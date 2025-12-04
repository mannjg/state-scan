package io.statescan.model;

import java.util.Map;

/**
 * An edge in the callgraph from caller to callee.
 *
 * @param caller          The calling method
 * @param callee          The called method
 * @param invokeType      Type of invocation
 * @param receiver        The actor receiving the call (null for static calls)
 * @param parameterFlow   Maps callee parameter index to the argument reference from caller
 * @param invocationIndex Index distinguishing multiple calls to same target in one method
 */
public record CallEdge(
    MethodRef caller,
    MethodRef callee,
    InvokeType invokeType,
    ArgumentRef receiver,
    Map<Integer, ArgumentRef> parameterFlow,
    int invocationIndex
) {
    public CallEdge {
        parameterFlow = Map.copyOf(parameterFlow);
    }

    /**
     * Unique key for this edge (caller -> callee @ index).
     */
    public String key() {
        return caller.key() + " -> " + callee.key() + "[" + invocationIndex + "]";
    }

    /**
     * Get the argument passed for a specific parameter index.
     */
    public ArgumentRef getArgument(int paramIndex) {
        return parameterFlow.get(paramIndex);
    }

    /**
     * Check if this is a static call.
     */
    public boolean isStatic() {
        return invokeType == InvokeType.STATIC;
    }

    /**
     * Check if this edge targets a method in the given package prefix.
     */
    public boolean calleeInPackage(String packagePrefix) {
        return callee.classFqn().startsWith(packagePrefix + ".");
    }

    /**
     * Check if this edge originates from a method in the given package prefix.
     */
    public boolean callerInPackage(String packagePrefix) {
        return caller.classFqn().startsWith(packagePrefix + ".");
    }
}
