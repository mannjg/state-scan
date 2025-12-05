package io.statescan.graph;

/**
 * Reference to a method, used as an edge in the call graph.
 *
 * @param owner      Fully qualified class name that owns this method
 * @param name       Method name
 * @param descriptor Method descriptor in JVM format (e.g., "(Ljava/lang/String;)V")
 */
public record MethodRef(
        String owner,
        String name,
        String descriptor
) {
    /**
     * Returns a human-readable signature.
     */
    public String toSignature() {
        return owner + "." + name + descriptor;
    }

    /**
     * Returns just the method name with owner (no descriptor).
     */
    public String toShortSignature() {
        return owner + "." + name;
    }

    /**
     * Checks if this is a constructor.
     */
    public boolean isConstructor() {
        return "<init>".equals(name);
    }

    /**
     * Checks if this is a static initializer.
     */
    public boolean isStaticInitializer() {
        return "<clinit>".equals(name);
    }

    /**
     * Converts internal class name (with /) to external format (with .).
     */
    public static String internalToExternal(String internalName) {
        return internalName.replace('/', '.');
    }

    /**
     * Converts external class name (with .) to internal format (with /).
     */
    public static String externalToInternal(String externalName) {
        return externalName.replace('.', '/');
    }
}
