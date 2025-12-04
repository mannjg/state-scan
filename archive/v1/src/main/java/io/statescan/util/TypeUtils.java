package io.statescan.util;

/**
 * Utility methods for type name handling.
 * Converts JVM bytecode descriptors to clean Java type names.
 */
public final class TypeUtils {

    private TypeUtils() {}

    /**
     * Converts JVM bytecode descriptor to clean Java type name.
     * <p>
     * Examples:
     * <ul>
     *   <li>"Lorg/apache/Class;" → "org.apache.Class"</li>
     *   <li>"[Ljava/lang/String;" → "String[]"</li>
     *   <li>"[[I" → "int[][]"</li>
     *   <li>"java.util.HashMap" → "java.util.HashMap" (pass-through)</li>
     * </ul>
     *
     * @param descriptor JVM type descriptor or already-clean type name
     * @return Clean Java type name
     */
    public static String cleanTypeName(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) {
            return descriptor;
        }

        // Count array dimensions
        int arrayDims = 0;
        int start = 0;
        while (start < descriptor.length() && descriptor.charAt(start) == '[') {
            arrayDims++;
            start++;
        }

        if (start >= descriptor.length()) {
            return descriptor;
        }

        String baseName;
        if (descriptor.charAt(start) == 'L') {
            // Object type: Lcom/example/Class; -> com.example.Class
            int end = descriptor.indexOf(';', start);
            if (end > start) {
                baseName = descriptor.substring(start + 1, end).replace('/', '.');
            } else {
                baseName = descriptor;
            }
        } else if (arrayDims > 0) {
            // Primitive array types
            baseName = switch (descriptor.charAt(start)) {
                case 'Z' -> "boolean";
                case 'B' -> "byte";
                case 'C' -> "char";
                case 'S' -> "short";
                case 'I' -> "int";
                case 'J' -> "long";
                case 'F' -> "float";
                case 'D' -> "double";
                case 'V' -> "void";
                default -> descriptor;
            };
        } else {
            // Already clean or unrecognized format - pass through
            baseName = descriptor.replace('/', '.');
        }

        // Add array brackets
        return baseName + "[]".repeat(arrayDims);
    }

    /**
     * Extracts simple class name from fully qualified name.
     * <p>
     * Examples:
     * <ul>
     *   <li>"java.util.HashMap" → "HashMap"</li>
     *   <li>"HashMap" → "HashMap"</li>
     *   <li>"java.util.Map$Entry" → "Map$Entry"</li>
     * </ul>
     *
     * @param fqcn Fully qualified class name
     * @return Simple class name (portion after last dot)
     */
    public static String simpleClassName(String fqcn) {
        if (fqcn == null) return null;
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }

    /**
     * Extracts simple class name, handling both clean names and bytecode descriptors.
     *
     * @param type Type descriptor or clean name
     * @return Simple class name
     */
    public static String simpleTypeName(String type) {
        return simpleClassName(cleanTypeName(type));
    }
}
