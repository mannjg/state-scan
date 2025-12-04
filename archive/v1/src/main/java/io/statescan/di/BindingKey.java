package io.statescan.di;

import java.util.Objects;

/**
 * Represents a DI binding key consisting of a type and optional qualifier.
 * <p>
 * In Guice/CDI, multiple providers can return the same type if they have different
 * qualifier annotations (e.g., {@code @Named("foo")} or custom qualifiers like
 * {@code @ExternalFooService}). This class enables proper matching between
 * qualified injection points and their corresponding providers.
 * <p>
 * Examples:
 * <ul>
 *   <li>{@code BindingKey.unqualified("com.example.OkHttpClient")} - matches any OkHttpClient</li>
 *   <li>{@code BindingKey.qualified("com.example.OkHttpClient", "ExternalFooService")} - matches only with @ExternalFooService qualifier</li>
 * </ul>
 */
public record BindingKey(String type, String qualifier) {

    /**
     * Creates a binding key for an unqualified type.
     *
     * @param type The fully qualified type name
     * @return A BindingKey with no qualifier
     */
    public static BindingKey unqualified(String type) {
        return new BindingKey(type, null);
    }

    /**
     * Creates a binding key for a qualified type.
     *
     * @param type      The fully qualified type name
     * @param qualifier The qualifier annotation simple name (e.g., "ExternalFooService", "Named")
     * @return A BindingKey with the specified qualifier
     */
    public static BindingKey qualified(String type, String qualifier) {
        return new BindingKey(type, qualifier);
    }

    /**
     * Creates a binding key, using unqualified if qualifier is null/empty.
     *
     * @param type      The fully qualified type name
     * @param qualifier The qualifier (may be null)
     * @return A BindingKey
     */
    public static BindingKey of(String type, String qualifier) {
        if (qualifier == null || qualifier.isBlank()) {
            return unqualified(type);
        }
        return qualified(type, qualifier);
    }

    /**
     * Checks if this binding key has a qualifier.
     *
     * @return true if qualified, false otherwise
     */
    public boolean isQualified() {
        return qualifier != null && !qualifier.isBlank();
    }

    /**
     * Returns a string representation suitable for use as a map key.
     * Format: "type" or "type#qualifier"
     *
     * @return String key representation
     */
    public String toKeyString() {
        return isQualified() ? type + "#" + qualifier : type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BindingKey that = (BindingKey) o;
        return Objects.equals(type, that.type) && Objects.equals(qualifier, that.qualifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, qualifier);
    }

    @Override
    public String toString() {
        return isQualified() ? type + "@" + qualifier : type;
    }
}
