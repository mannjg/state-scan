package io.statescan.model;

/**
 * Reference to a value passed as an argument to a method invocation.
 * Can represent actors (FIELD, PARAM, LOCAL, etc.), literals, or computed values.
 */
public sealed interface ArgumentRef {

    /**
     * Argument sourced from an actor (field, parameter, local variable, etc.)
     */
    record ActorArg(ActorType actorType, String name, String typeFqn) implements ArgumentRef {}

    /**
     * Argument is a literal constant (String, number, null, class literal).
     */
    record LiteralArg(Object value, String typeFqn) implements ArgumentRef {}

    /**
     * Argument is a computed/unknown value (result of expression, method return, etc.)
     */
    record ComputedArg(String typeFqn) implements ArgumentRef {}

    /**
     * Argument is 'this' reference.
     */
    record ThisArg(String typeFqn) implements ArgumentRef {}

    /**
     * Get the type FQN of this argument reference.
     */
    default String typeFqn() {
        if (this instanceof ActorArg a) {
            return a.typeFqn();
        } else if (this instanceof LiteralArg l) {
            return l.typeFqn();
        } else if (this instanceof ComputedArg c) {
            return c.typeFqn();
        } else if (this instanceof ThisArg t) {
            return t.typeFqn();
        }
        throw new IllegalStateException("Unknown ArgumentRef type: " + this.getClass());
    }

    /**
     * Check if this is a trackable actor (field, parameter, or local).
     */
    default boolean isActor() {
        return this instanceof ActorArg;
    }

    /**
     * Get a human-readable description of this argument.
     */
    default String describe() {
        if (this instanceof ActorArg a) {
            return a.actorType() + " " + a.name() + " (" + a.typeFqn() + ")";
        } else if (this instanceof LiteralArg l) {
            return "literal " + l.value() + " (" + l.typeFqn() + ")";
        } else if (this instanceof ComputedArg c) {
            return "computed (" + c.typeFqn() + ")";
        } else if (this instanceof ThisArg t) {
            return "this (" + t.typeFqn() + ")";
        }
        throw new IllegalStateException("Unknown ArgumentRef type: " + this.getClass());
    }
}
