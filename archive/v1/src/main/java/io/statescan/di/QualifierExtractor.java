package io.statescan.di;

import java.util.Set;

/**
 * Utility class for extracting qualifier annotations from DI injection points.
 * <p>
 * A qualifier distinguishes between multiple bindings of the same type.
 * Common qualifiers include:
 * <ul>
 *   <li>{@code @Named("value")} - standard javax/jakarta qualifier</li>
 *   <li>Custom annotations annotated with {@code @Qualifier} or {@code @BindingAnnotation}</li>
 * </ul>
 */
public final class QualifierExtractor {

    /**
     * Annotations that are NOT qualifiers - these are scope/lifecycle/framework annotations.
     */
    private static final Set<String> NON_QUALIFIER_ANNOTATIONS = Set.of(
            // Guice
            "com.google.inject.Provides",
            "com.google.inject.Singleton",
            "com.google.inject.Inject",
            "com.google.inject.Exposed",
            "com.google.inject.assistedinject.Assisted",
            "com.google.inject.assistedinject.AssistedInject",
            // javax.inject
            "javax.inject.Singleton",
            "javax.inject.Inject",
            // jakarta.inject
            "jakarta.inject.Singleton",
            "jakarta.inject.Inject",
            // CDI scopes
            "javax.enterprise.context.ApplicationScoped",
            "javax.enterprise.context.RequestScoped",
            "javax.enterprise.context.SessionScoped",
            "javax.enterprise.context.Dependent",
            "jakarta.enterprise.context.ApplicationScoped",
            "jakarta.enterprise.context.RequestScoped",
            "jakarta.enterprise.context.SessionScoped",
            "jakarta.enterprise.context.Dependent",
            // Common annotations
            "java.lang.Override",
            "java.lang.Deprecated",
            "java.lang.SuppressWarnings",
            // Nullable annotations
            "javax.annotation.Nullable",
            "jakarta.annotation.Nullable",
            "org.jetbrains.annotations.Nullable",
            "org.checkerframework.checker.nullness.qual.Nullable",
            // Other common annotations
            "lombok.Getter",
            "lombok.Setter",
            "lombok.Data",
            "lombok.Value",
            "lombok.Builder",
            "com.fasterxml.jackson.annotation.JsonProperty",
            "com.fasterxml.jackson.annotation.JsonIgnore"
    );

    /**
     * Annotations that indicate a field is an injection point.
     */
    private static final Set<String> INJECT_ANNOTATIONS = Set.of(
            "javax.inject.Inject",
            "jakarta.inject.Inject",
            "com.google.inject.Inject"
    );

    private QualifierExtractor() {
        // Utility class
    }

    /**
     * Checks if a field/method/parameter has an @Inject annotation.
     *
     * @param annotations Set of fully qualified annotation class names
     * @return true if has @Inject annotation
     */
    public static boolean isInjectionPoint(Set<String> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        for (String ann : annotations) {
            if (INJECT_ANNOTATIONS.contains(ann)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the qualifier annotation from a set of annotations.
     * <p>
     * A qualifier is any annotation that is:
     * <ul>
     *   <li>Not a known non-qualifier (scope, lifecycle, framework annotation)</li>
     *   <li>Not from standard framework packages</li>
     * </ul>
     * <p>
     * Special handling for @Named - returns "Named" (value extraction not yet supported).
     *
     * @param annotations Set of fully qualified annotation class names
     * @return The qualifier simple name, or null if no qualifier found
     */
    public static String extractQualifier(Set<String> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return null;
        }

        for (String ann : annotations) {
            // Skip known non-qualifier annotations
            if (NON_QUALIFIER_ANNOTATIONS.contains(ann)) {
                continue;
            }

            // Skip framework packages (but allow @Named)
            if (ann.startsWith("com.google.inject.") && !ann.contains("Named")) {
                continue;
            }
            if (ann.startsWith("javax.inject.") && !ann.contains("Named")) {
                continue;
            }
            if (ann.startsWith("jakarta.inject.") && !ann.contains("Named")) {
                continue;
            }
            if (ann.startsWith("java.lang.")) {
                continue;
            }
            if (ann.startsWith("javax.annotation.") && !ann.contains("Named")) {
                continue;
            }
            if (ann.startsWith("jakarta.annotation.") && !ann.contains("Named")) {
                continue;
            }

            // This looks like a qualifier - extract simple name
            return extractSimpleName(ann);
        }

        return null;
    }

    /**
     * Creates a BindingKey for an injection point based on its type and annotations.
     *
     * @param type The field/parameter type (fully qualified)
     * @param annotations The annotations on the injection point
     * @return A BindingKey with type and optional qualifier
     */
    public static BindingKey createBindingKey(String type, Set<String> annotations) {
        String qualifier = extractQualifier(annotations);
        return BindingKey.of(type, qualifier);
    }

    /**
     * Extracts the simple class name from a fully qualified name.
     * E.g., "com.example.ExternalFooService" -> "ExternalFooService"
     */
    private static String extractSimpleName(String fqn) {
        if (fqn == null) return null;
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }
}
