package io.statescan.graph;

import io.statescan.di.BindingKey;
import io.statescan.di.QualifierExtractor;

import java.util.Set;

/**
 * Represents a method parameter with its type and annotations.
 * Used primarily for tracking injection points in @Inject constructors
 * and @Provides method parameters.
 */
public record ParameterNode(
        int index,
        String type,              // FQN of the parameter type
        Set<String> annotations   // Parameter-level annotations
) {
    /**
     * Creates a ParameterNode with immutable annotations.
     */
    public ParameterNode {
        annotations = annotations != null ? Set.copyOf(annotations) : Set.of();
    }

    /**
     * Extracts the qualifier annotation from this parameter's annotations.
     *
     * @return The qualifier simple name, or null if no qualifier
     */
    public String qualifier() {
        return QualifierExtractor.extractQualifier(annotations);
    }

    /**
     * Creates a BindingKey for this parameter (type + qualifier).
     * Used for looking up DI bindings.
     *
     * @return BindingKey for this injection point
     */
    public BindingKey toBindingKey() {
        return BindingKey.of(type, qualifier());
    }

    /**
     * Checks if this parameter has a specific annotation.
     *
     * @param annotationClass Fully qualified annotation class name
     * @return true if the annotation is present
     */
    public boolean hasAnnotation(String annotationClass) {
        return annotations.contains(annotationClass);
    }

    /**
     * Checks if this parameter is an injection point (has @Inject or similar).
     * Note: For constructor parameters in an @Inject constructor, all parameters
     * are injection points even without individual @Inject annotations.
     *
     * @return true if has @Inject annotation on the parameter itself
     */
    public boolean hasInjectAnnotation() {
        return QualifierExtractor.isInjectionPoint(annotations);
    }
}
