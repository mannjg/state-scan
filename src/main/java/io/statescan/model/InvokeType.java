package io.statescan.model;

/**
 * Type of method invocation instruction.
 */
public enum InvokeType {
    /** Instance method call via vtable (invokevirtual) */
    VIRTUAL,
    /** Interface method call (invokeinterface) */
    INTERFACE,
    /** Static method call (invokestatic) */
    STATIC,
    /** Constructor, super call, or private method (invokespecial) */
    SPECIAL
}
