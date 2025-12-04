package io.statescan.graph;

/**
 * Types of edges in the call graph that connect classes.
 */
public enum EdgeType {
    /** Edge via a field declaration (class has a field of the target type) */
    FIELD,

    /** Edge via a method invocation (class calls a method on the target type) */
    INVOCATION,

    /** Edge via inheritance (extends superclass or implements interface) */
    INHERITANCE,

    /** Edge via DI binding resolution (interface -> concrete implementation) */
    DI_BINDING
}
