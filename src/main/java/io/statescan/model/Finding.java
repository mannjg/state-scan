package io.statescan.model;

import java.util.List;
import java.util.Optional;

/**
 * Represents a single stateful component finding.
 *
 * @param className         Fully qualified class name where the state was found
 * @param lineNumber        Line number in source (if available from debug info)
 * @param stateType         Category of state (IN_MEMORY, EXTERNAL, etc.)
 * @param riskLevel         Severity of the finding
 * @param pattern           Description of the pattern detected
 * @param fieldName         Name of the field holding state (if applicable)
 * @param fieldType         Type of the field (if applicable)
 * @param scopeSource       How the scope was determined (annotation, module binding, etc.)
 * @param scopeAnnotation   The specific annotation that defined scope (if applicable)
 * @param scopeModule       The module that defined scope (if applicable)
 * @param description       Human-readable description of the finding
 * @param recommendation    Recommended action to address the finding
 * @param detectorId        ID of the detector that found this issue
 * @param sourceFile        Source file name (if available)
 * @param details           Additional details about the finding
 * @param affectedEndpoints REST endpoints that access this stateful component
 */
public record Finding(
        String className,
        int lineNumber,
        StateType stateType,
        RiskLevel riskLevel,
        String pattern,
        String fieldName,
        String fieldType,
        ScopeSource scopeSource,
        String scopeAnnotation,
        String scopeModule,
        String description,
        String recommendation,
        String detectorId,
        String sourceFile,
        String details,
        List<String> affectedEndpoints
) {
    /**
     * How the scope of a class was determined.
     */
    public enum ScopeSource {
        /**
         * Scope determined by annotation on the class itself.
         * e.g., @Singleton, @ApplicationScoped
         */
        ANNOTATION("Annotation"),

        /**
         * Scope determined by Guice module binding.
         * e.g., bind(X.class).in(Singleton.class)
         */
        MODULE_BINDING("Module Binding"),

        /**
         * Scope determined by @Provides method annotation.
         * e.g., @Provides @Singleton
         */
        PROVIDER_METHOD("Provider Method"),

        /**
         * Static field - inherently singleton-like scope.
         */
        STATIC_FIELD("Static Field"),

        /**
         * Scope not applicable or unknown.
         */
        NONE("N/A");

        private final String displayName;

        ScopeSource(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * Compact constructor with validation.
     */
    public Finding {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className cannot be null or blank");
        }
        if (stateType == null) {
            throw new IllegalArgumentException("stateType cannot be null");
        }
        if (riskLevel == null) {
            throw new IllegalArgumentException("riskLevel cannot be null");
        }
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern cannot be null or blank");
        }
        if (scopeSource == null) {
            scopeSource = ScopeSource.NONE;
        }
        if (affectedEndpoints == null) {
            affectedEndpoints = List.of();
        }
    }

    /**
     * Builder for creating Finding instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String className;
        private int lineNumber = -1;
        private StateType stateType;
        private RiskLevel riskLevel;
        private String pattern;
        private String fieldName;
        private String fieldType;
        private ScopeSource scopeSource = ScopeSource.NONE;
        private String scopeAnnotation;
        private String scopeModule;
        private String description;
        private String recommendation;
        private String detectorId;
        private String sourceFile;
        private String details;
        private List<String> affectedEndpoints = List.of();

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder stateType(StateType stateType) {
            this.stateType = stateType;
            return this;
        }

        public Builder riskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder fieldType(String fieldType) {
            this.fieldType = fieldType;
            return this;
        }

        public Builder scopeSource(ScopeSource scopeSource) {
            this.scopeSource = scopeSource;
            return this;
        }

        public Builder scopeAnnotation(String scopeAnnotation) {
            this.scopeAnnotation = scopeAnnotation;
            return this;
        }

        public Builder scopeModule(String scopeModule) {
            this.scopeModule = scopeModule;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder recommendation(String recommendation) {
            this.recommendation = recommendation;
            return this;
        }

        public Builder detectorId(String detectorId) {
            this.detectorId = detectorId;
            return this;
        }

        public Builder sourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder affectedEndpoints(List<String> endpoints) {
            this.affectedEndpoints = endpoints != null ? List.copyOf(endpoints) : List.of();
            return this;
        }

        public Finding build() {
            return new Finding(
                    className,
                    lineNumber,
                    stateType,
                    riskLevel,
                    pattern,
                    fieldName,
                    fieldType,
                    scopeSource,
                    scopeAnnotation,
                    scopeModule,
                    description,
                    recommendation,
                    detectorId,
                    sourceFile,
                    details,
                    affectedEndpoints
            );
        }
    }

    /**
     * Returns a display-friendly location string.
     */
    public String location() {
        if (lineNumber > 0) {
            return className + ":" + lineNumber;
        }
        return className;
    }

    /**
     * Returns the simple class name (without package).
     */
    public String simpleClassName() {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    /**
     * Returns the field signature if field info is available.
     */
    public Optional<String> fieldSignature() {
        if (fieldName == null || fieldName.isBlank()) {
            return Optional.empty();
        }
        if (fieldType != null && !fieldType.isBlank()) {
            return Optional.of(fieldType + " " + fieldName);
        }
        return Optional.of(fieldName);
    }
}
