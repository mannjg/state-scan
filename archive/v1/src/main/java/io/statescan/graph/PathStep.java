package io.statescan.graph;

import io.statescan.util.TypeUtils;

/**
 * Represents a single step in a call path, identifying the specific method or 
 * class-level relationship that forms part of the call chain.
 * <p>
 * <b>Key semantic:</b> memberName identifies the SOURCE method in THIS class that 
 * creates the outgoing edge to the next step. This enables accurate method-level
 * call chain tracking.
 * <p>
 * Examples:
 * <ul>
 *   <li>INVOCATION: ServiceA#handleRequest -> Repository#save means 
 *       handleRequest() in ServiceA calls save() in Repository</li>
 *   <li>FIELD: ServiceA#dataSource means ServiceA accesses field dataSource</li>
 *   <li>INHERITANCE: ServiceA#extends means ServiceA extends the next class</li>
 * </ul>
 *
 * @param className  Fully qualified class name at this step
 * @param memberName The method/field in THIS class that creates the outgoing edge (null for root/leaf)
 * @param edgeType   How this step connects to the next (null for root/leaf nodes)
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
