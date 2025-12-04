package io.statescan.model;

/**
 * Unique reference to a method in the codebase.
 *
 * @param classFqn    Fully qualified class name
 * @param methodName  Method name
 * @param descriptor  JVM method descriptor
 */
public record MethodRef(
    String classFqn,
    String methodName,
    String descriptor
) {
    /**
     * Returns a unique key string for this method.
     */
    public String key() {
        return classFqn + "#" + methodName + descriptor;
    }

    /**
     * Returns a human-readable signature.
     */
    public String signature() {
        return classFqn + "." + methodName + descriptor;
    }

    /**
     * Create from ClassInfo and MethodInfo.
     */
    public static MethodRef of(ClassInfo classInfo, MethodInfo methodInfo) {
        return new MethodRef(classInfo.fqn(), methodInfo.name(), methodInfo.descriptor());
    }

    /**
     * Create from class FQN and method key (name + descriptor).
     */
    public static MethodRef of(String classFqn, String methodKey) {
        // Method key format: "methodName(descriptor)"
        int parenIdx = methodKey.indexOf('(');
        if (parenIdx > 0) {
            return new MethodRef(classFqn, methodKey.substring(0, parenIdx), methodKey.substring(parenIdx));
        }
        // Fallback if no descriptor
        return new MethodRef(classFqn, methodKey, "");
    }
}
