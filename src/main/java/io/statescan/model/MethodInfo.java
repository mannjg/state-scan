package io.statescan.model;

import java.util.Set;

/**
 * Information about a method within a class.
 *
 * @param name        Method name (e.g., "getUser", "&lt;init&gt;" for constructors)
 * @param descriptor  JVM method descriptor (e.g., "(Ljava/lang/String;)V")
 * @param actors      Set of actors employed by this method
 * @param isStatic    Whether this is a static method
 * @param isPublic    Whether this is a public method
 */
public record MethodInfo(
    String name,
    String descriptor,
    Set<Actor> actors,
    boolean isStatic,
    boolean isPublic
) {
    /**
     * Create a MethodInfo with an immutable copy of the actors set.
     */
    public MethodInfo {
        actors = Set.copyOf(actors);
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
