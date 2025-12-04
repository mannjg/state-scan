package io.statescan.graph;

import io.statescan.util.TypeUtils;

/**
 * Represents a single step in a call path, including the class and the member
 * (field or method) that leads to the next step.
 * <p>
 * For example, if class A has field "dataSource" of type B, and we traverse from A to B,
 * the PathStep for A would be: (className="com.example.A", memberName="dataSource", edgeType=FIELD)
 *
 * @param className  Fully qualified class name at this step
 * @param memberName The field/method name that leads to the next step (null for leaf nodes)
 * @param edgeType   How this step connects to the next (null for leaf nodes)
 */
public record PathStep(
        String className,
        String memberName,
        EdgeType edgeType
) {
    /**
     * Creates a root step (first node in path, no incoming edge).
     */
    public static PathStep root(String className) {
        return new PathStep(className, null, null);
    }

    /**
     * Creates a step reached via a field.
     */
    public static PathStep viaField(String className, String fieldName) {
        return new PathStep(className, fieldName, EdgeType.FIELD);
    }

    /**
     * Creates a step reached via a method invocation.
     */
    public static PathStep viaInvocation(String className, String methodName) {
        return new PathStep(className, methodName, EdgeType.INVOCATION);
    }

    /**
     * Creates a step reached via inheritance (extends/implements).
     */
    public static PathStep viaInheritance(String className, String relation) {
        return new PathStep(className, relation, EdgeType.INHERITANCE);
    }

    /**
     * Creates a step reached via DI binding resolution.
     */
    public static PathStep viaDI(String className, String bindingInfo) {
        return new PathStep(className, bindingInfo, EdgeType.DI_BINDING);
    }

    /**
     * Returns the simple class name (without package).
     */
    public String simpleClassName() {
        return TypeUtils.simpleClassName(className);
    }

    /**
     * Formats this step for display.
     * <p>
     * Format: "ClassName#member" or just "ClassName" if no member.
     * Uses simple class name for project classes, FQCN for third-party.
     *
     * @param useSimpleName true to use simple class name, false for FQCN
     */
    public String formatted(boolean useSimpleName) {
        String name = useSimpleName ? simpleClassName() : className;
        if (memberName == null || memberName.isEmpty()) {
            return name;
        }
        return name + "#" + memberName;
    }

    /**
     * Formats with simple class name by default.
     */
    public String formatted() {
        return formatted(true);
    }
}
