package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.CallGraph;
import io.statescan.graph.ClassNode;
import io.statescan.graph.FieldNode;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.StateType;

import java.util.*;

/**
 * Detects singleton-scoped classes that contain mutable state.
 *
 * Singletons with mutable fields are problematic in replicated environments
 * because each replica has its own instance with its own state.
 */
public class SingletonDetector implements Detector {

    @Override
    public String id() {
        return "singleton";
    }

    @Override
    public String description() {
        return "Detects singleton classes with mutable state";
    }

    @Override
    public List<Finding> detect(CallGraph graph, LeafTypeConfig config) {
        List<Finding> findings = new ArrayList<>();

        for (ClassNode cls : graph.allClasses()) {
            // Only analyze project classes
            if (!cls.isProjectClass()) {
                continue;
            }

            // Check if class has singleton annotation
            String singletonAnnotation = findSingletonAnnotation(cls, config);
            if (singletonAnnotation == null) {
                continue;
            }

            // Find mutable fields in this singleton
            List<FieldNode> mutableFields = findMutableFields(cls, config);

            if (!mutableFields.isEmpty()) {
                for (FieldNode field : mutableFields) {
                    Finding finding = createFinding(cls, field, singletonAnnotation, config);
                    findings.add(finding);
                }
            }
        }

        return findings;
    }

    private String findSingletonAnnotation(ClassNode cls, LeafTypeConfig config) {
        for (String annotation : cls.annotations()) {
            if (config.isSingletonAnnotation(annotation)) {
                return annotation;
            }
        }
        return null;
    }

    private List<FieldNode> findMutableFields(ClassNode cls, LeafTypeConfig config) {
        List<FieldNode> mutable = new ArrayList<>();

        for (FieldNode field : cls.fields()) {
            // Skip static fields (handled by StaticStateDetector)
            if (field.isStatic()) {
                continue;
            }

            // Skip safe types
            String typeName = field.extractTypeName();
            if (typeName != null && config.isSafeType(typeName)) {
                continue;
            }

            // Check if field is mutable
            if (field.isPotentiallyMutable()) {
                mutable.add(field);
            }
        }

        return mutable;
    }

    private Finding createFinding(ClassNode cls, FieldNode field, String annotation, LeafTypeConfig config) {
        String typeName = field.extractTypeName();
        RiskLevel risk = determineRisk(field, typeName, config);

        return Finding.builder()
                .className(cls.fqn())
                .fieldName(field.name())
                .fieldType(field.type())
                .stateType(StateType.IN_MEMORY)
                .riskLevel(risk)
                .pattern("Singleton with mutable field")
                .scopeSource(Finding.ScopeSource.ANNOTATION)
                .scopeAnnotation(annotation)
                .description(buildDescription(cls, field, annotation))
                .recommendation(buildRecommendation(field, typeName, config))
                .detectorId(id())
                .sourceFile(cls.sourceFile())
                .build();
    }

    private RiskLevel determineRisk(FieldNode field, String typeName, LeafTypeConfig config) {
        if (typeName != null) {
            if (config.isCacheType(typeName)) {
                return RiskLevel.CRITICAL;
            }
            if (config.isMutableCollectionType(typeName)) {
                return RiskLevel.CRITICAL;
            }
            if (config.isResilienceType(typeName)) {
                return RiskLevel.HIGH;
            }
        }

        // Non-final field can be reassigned
        if (!field.isFinal()) {
            return RiskLevel.HIGH;
        }

        return RiskLevel.MEDIUM;
    }

    private String buildDescription(ClassNode cls, FieldNode field, String annotation) {
        StringBuilder sb = new StringBuilder();
        sb.append("Class '").append(cls.simpleName()).append("' ");
        sb.append("is a singleton (").append(simplifyAnnotation(annotation)).append(") ");
        sb.append("with mutable field '").append(field.name()).append("'. ");
        sb.append("This state is NOT shared across replicas - each instance has its own copy.");
        return sb.toString();
    }

    private String buildRecommendation(FieldNode field, String typeName, LeafTypeConfig config) {
        if (typeName != null && config.isCacheType(typeName)) {
            return "Replace in-memory cache with distributed cache (Redis, Hazelcast)";
        }
        if (typeName != null && config.isMutableCollectionType(typeName)) {
            return "Store data in database or distributed cache instead of in-memory collection";
        }
        if (!field.isFinal()) {
            return "Make field final and immutable, or externalize state to shared storage";
        }
        return "Consider if this state needs to be shared across replicas";
    }

    private String simplifyAnnotation(String annotation) {
        int lastDot = annotation.lastIndexOf('.');
        return lastDot >= 0 ? "@" + annotation.substring(lastDot + 1) : "@" + annotation;
    }
}
