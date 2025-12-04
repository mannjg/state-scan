package io.statescan.model;

import java.util.Set;

/**
 * An entity that receives method calls within a method body.
 *
 * @param type          The kind of actor (FIELD, PARAMETER, LOCAL, STATIC_CLASS, NEW_OBJECT)
 * @param name          The actor's name (field name, param name, class simple name for static)
 * @param typeFqn       The declared type FQN of the actor
 * @param methodsCalled Set of method names called on this actor
 */
public record Actor(
    ActorType type,
    String name,
    String typeFqn,
    Set<String> methodsCalled
) {
    /**
     * Create an Actor with an immutable copy of the methods called set.
     */
    public Actor {
        methodsCalled = Set.copyOf(methodsCalled);
    }

    /**
     * Returns a unique key for this actor based on type, name, and typeFqn.
     */
    public String key() {
        return type + ":" + name + ":" + typeFqn;
    }
}
