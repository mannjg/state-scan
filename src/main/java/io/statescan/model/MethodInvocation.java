package io.statescan.model;

import java.util.List;

/**
 * Represents a method invocation captured during bytecode analysis.
 *
 * @param targetClassFqn   FQN of the class/interface declaring the method being called
 * @param methodName       Name of the method being called
 * @param methodDescriptor JVM method descriptor (e.g., "(Ljava/lang/String;I)V")
 * @param invokeType       Type of invocation (VIRTUAL, STATIC, INTERFACE, SPECIAL)
 * @param receiver         The actor receiving the call (null for static calls)
 * @param arguments        List of argument references in parameter order
 * @param bytecodeOffset   Approximate location in the method (for ordering/debugging)
 */
public record MethodInvocation(
    String targetClassFqn,
    String methodName,
    String methodDescriptor,
    InvokeType invokeType,
    ArgumentRef receiver,
    List<ArgumentRef> arguments,
    int bytecodeOffset
) {
    public MethodInvocation {
        arguments = List.copyOf(arguments);
    }

    /**
     * Returns a unique key for the target method.
     */
    public String targetMethodKey() {
        return targetClassFqn + "#" + methodName + methodDescriptor;
    }

    /**
     * Returns a MethodRef for the target.
     */
    public MethodRef targetRef() {
        return new MethodRef(targetClassFqn, methodName, methodDescriptor);
    }

    /**
     * Returns a human-readable signature.
     */
    public String targetSignature() {
        return targetClassFqn + "." + methodName + methodDescriptor;
    }

    /**
     * Check if this is a static method call.
     */
    public boolean isStatic() {
        return invokeType == InvokeType.STATIC;
    }

    /**
     * Check if this is a constructor call.
     */
    public boolean isConstructor() {
        return "<init>".equals(methodName);
    }
}
