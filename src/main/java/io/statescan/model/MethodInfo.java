package io.statescan.model;

import java.util.List;
import java.util.Set;

/**
 * Information about a method within a class.
 *
 * @param name        Method name (e.g., "getUser", "&lt;init&gt;" for constructors)
 * @param descriptor  JVM method descriptor (e.g., "(Ljava/lang/String;)V")
 * @param actors      Set of actors employed by this method
 * @param invocations List of method invocations made by this method
 * @param isStatic    Whether this is a static method
 * @param isPublic    Whether this is a public method
 */
public record MethodInfo(
    String name,
    String descriptor,
    Set<Actor> actors,
    List<MethodInvocation> invocations,
    boolean isStatic,
    boolean isPublic
) {
    /**
     * Create a MethodInfo with immutable copies of collections.
     */
    public MethodInfo {
        actors = Set.copyOf(actors);
        invocations = List.copyOf(invocations);
    }

    /**
     * Returns a unique key for this method (name + descriptor handles overloads).
     */
    public String key() {
        return name + descriptor;
    }

    /**
     * Returns true if this is a constructor.
     */
    public boolean isConstructor() {
        return "<init>".equals(name);
    }

    /**
     * Returns true if this is a static initializer.
     */
    public boolean isStaticInitializer() {
        return "<clinit>".equals(name);
    }
}
