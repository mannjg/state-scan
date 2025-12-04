package io.statescan.bytecode;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for parsing JVM method descriptors.
 * <p>
 * Method descriptors follow the format: (ParameterTypes)ReturnType
 * <p>
 * Type encodings:
 * <ul>
 *   <li>B - byte</li>
 *   <li>C - char</li>
 *   <li>D - double</li>
 *   <li>F - float</li>
 *   <li>I - int</li>
 *   <li>J - long</li>
 *   <li>S - short</li>
 *   <li>Z - boolean</li>
 *   <li>V - void (return type only)</li>
 *   <li>L&lt;classname&gt;; - object type</li>
 *   <li>[ - array (prefix)</li>
 * </ul>
 */
public final class DescriptorParser {

    private DescriptorParser() {
        // Utility class
    }

    /**
     * Parses a method descriptor and returns the parameter types as FQNs.
     * Primitive types are returned as their Java names (int, boolean, etc.).
     *
     * @param descriptor The method descriptor (e.g., "(Ljava/lang/String;I)V")
     * @return List of parameter type FQNs in order
     */
    public static List<String> parseParameterTypes(String descriptor) {
        List<String> types = new ArrayList<>();

        if (descriptor == null || !descriptor.startsWith("(")) {
            return types;
        }

        int pos = 1; // Skip opening '('
        int endParams = descriptor.indexOf(')');

        if (endParams < 0) {
            return types;
        }

        while (pos < endParams) {
            ParseResult result = parseType(descriptor, pos);
            if (result == null) {
                break; // Parse error
            }
            types.add(result.type);
            pos = result.endPos;
        }

        return types;
    }

    /**
     * Parses the return type from a method descriptor.
     *
     * @param descriptor The method descriptor
     * @return The return type FQN, or null if invalid
     */
    public static String parseReturnType(String descriptor) {
        if (descriptor == null) {
            return null;
        }

        int returnStart = descriptor.indexOf(')');
        if (returnStart < 0 || returnStart + 1 >= descriptor.length()) {
            return null;
        }

        ParseResult result = parseType(descriptor, returnStart + 1);
        return result != null ? result.type : null;
    }

    /**
     * Parses a single type from a descriptor starting at the given position.
     *
     * @param descriptor The descriptor string
     * @param pos        Starting position
     * @return ParseResult with type and end position, or null on error
     */
    private static ParseResult parseType(String descriptor, int pos) {
        if (pos >= descriptor.length()) {
            return null;
        }

        char c = descriptor.charAt(pos);

        // Primitives
        switch (c) {
            case 'B':
                return new ParseResult("byte", pos + 1);
            case 'C':
                return new ParseResult("char", pos + 1);
            case 'D':
                return new ParseResult("double", pos + 1);
            case 'F':
                return new ParseResult("float", pos + 1);
            case 'I':
                return new ParseResult("int", pos + 1);
            case 'J':
                return new ParseResult("long", pos + 1);
            case 'S':
                return new ParseResult("short", pos + 1);
            case 'Z':
                return new ParseResult("boolean", pos + 1);
            case 'V':
                return new ParseResult("void", pos + 1);
        }

        // Array type
        if (c == '[') {
            ParseResult elementType = parseType(descriptor, pos + 1);
            if (elementType == null) {
                return null;
            }
            return new ParseResult(elementType.type + "[]", elementType.endPos);
        }

        // Object type: L<classname>;
        if (c == 'L') {
            int semicolon = descriptor.indexOf(';', pos);
            if (semicolon < 0) {
                return null;
            }
            String className = descriptor.substring(pos + 1, semicolon).replace('/', '.');
            return new ParseResult(className, semicolon + 1);
        }

        return null; // Unknown type
    }

    /**
     * Checks if a type is a primitive type.
     *
     * @param type The type name
     * @return true if primitive
     */
    public static boolean isPrimitive(String type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case "byte", "char", "double", "float", "int", "long", "short", "boolean", "void" -> true;
            default -> false;
        };
    }

    /**
     * Checks if a type is an array type.
     *
     * @param type The type name
     * @return true if array
     */
    public static boolean isArray(String type) {
        return type != null && type.endsWith("[]");
    }

    /**
     * Gets the element type of an array type.
     *
     * @param arrayType The array type (e.g., "java.lang.String[]")
     * @return The element type, or the original type if not an array
     */
    public static String getArrayElementType(String arrayType) {
        if (!isArray(arrayType)) {
            return arrayType;
        }
        return arrayType.substring(0, arrayType.length() - 2);
    }

    /**
     * Result of parsing a single type.
     */
    private record ParseResult(String type, int endPos) {
    }
}
