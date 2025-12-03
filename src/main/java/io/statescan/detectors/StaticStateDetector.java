package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.CallGraph;
import io.statescan.graph.ClassNode;
import io.statescan.graph.FieldNode;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.StateType;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects static mutable fields that could cause issues in replicated environments.
 *
 * Static mutable state is shared within a JVM but NOT across replicas,
 * leading to data inconsistency when horizontally scaling.
 */
public class StaticStateDetector implements Detector {

    @Override
    public String id() {
        return "static-state";
    }

    @Override
    public String description() {
        return "Detects static mutable fields (non-final or mutable type)";
    }

    @Override
    public List<Finding> detect(CallGraph graph, LeafTypeConfig config) {
        List<Finding> findings = new ArrayList<>();

        for (ClassNode cls : graph.allClasses()) {
            // Only analyze project classes
            if (!cls.isProjectClass()) {
                continue;
            }

            for (FieldNode field : cls.fields()) {
                // Only check static fields
                if (!field.isStatic()) {
                    continue;
                }

                // Skip synthetic enum $VALUES field
                if ("$VALUES".equals(field.name())) {
                    continue;
                }

                String fieldType = field.extractTypeName();

                // Skip enum constant fields (where field type is same as declaring class)
                // Only skip for actual enums, not singleton pattern classes
                if (cls.isEnum() && fieldType != null && fieldType.equals(cls.fqn())) {
                    continue;
                }

                // Skip safe types like loggers
                if (fieldType != null && config.isSafeType(fieldType)) {
                    continue;
                }

                // Check if field is mutable using BOTH heuristic AND config
                boolean isMutable = isMutableField(field, fieldType, config);
                if (!isMutable) {
                    continue;
                }

                RiskLevel risk = determineRisk(field, config);
                String pattern = determinePattern(field, fieldType, config);

                Finding finding = Finding.builder()
                        .className(cls.fqn())
                        .fieldName(field.name())
                        .fieldType(field.type())
                        .stateType(StateType.IN_MEMORY)
                        .riskLevel(risk)
                        .pattern(pattern)
                        .description(buildDescription(field))
                        .recommendation(buildRecommendation(field, config))
                        .detectorId(id())
                        .sourceFile(cls.sourceFile())
                        .build();

                findings.add(finding);
            }
        }

        return findings;
    }

    /**
     * Checks if a static field is mutable using both heuristics and config.
     */
    private boolean isMutableField(FieldNode field, String fieldType, LeafTypeConfig config) {
        // Non-final static fields are always mutable
        if (!field.isFinal()) {
            return true;
        }

        // Check heuristic-based detection
        if (field.isPotentiallyMutable()) {
            return true;
        }

        // Check config-based detection for known mutable types
        if (fieldType != null) {
            if (config.isMutableCollectionType(fieldType)) {
                return true;
            }
            if (config.isCacheType(fieldType)) {
                return true;
            }
            if (config.isThreadLocalType(fieldType)) {
                return true;
            }
        }

        return false;
    }

    private RiskLevel determineRisk(FieldNode field, LeafTypeConfig config) {
        String typeName = field.extractTypeName();

        // Static non-final is always critical
        if (!field.isFinal()) {
            return RiskLevel.CRITICAL;
        }

        // Static final but mutable type (collections, atomic, etc.)
        if (typeName != null) {
            if (config.isCacheType(typeName)) {
                return RiskLevel.CRITICAL;
            }
            if (config.isMutableCollectionType(typeName)) {
                return RiskLevel.HIGH;
            }
            if (config.isThreadLocalType(typeName)) {
                return RiskLevel.HIGH;
            }
        }

        return RiskLevel.MEDIUM;
    }

    private String determinePattern(FieldNode field, String fieldType, LeafTypeConfig config) {
        if (!field.isFinal()) {
            return "Mutable static field (non-final)";
        }
        if (field.isPotentiallyMutable()) {
            return "Static final field with mutable type";
        }
        if (fieldType != null && config.isMutableCollectionType(fieldType)) {
            return "Static final field with mutable type (" + simpleTypeName(fieldType) + ")";
        }
        if (fieldType != null && config.isCacheType(fieldType)) {
            return "Static final cache field";
        }
        if (fieldType != null && config.isThreadLocalType(fieldType)) {
            return "Static ThreadLocal field";
        }
        return "Static mutable field";
    }

    private String simpleTypeName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    private String buildDescription(FieldNode field) {
        StringBuilder sb = new StringBuilder();
        sb.append("Static ");
        if (!field.isFinal()) {
            sb.append("non-final ");
        } else {
            sb.append("final ");
        }
        sb.append("field '").append(field.name()).append("'");

        if (field.isPotentiallyMutable()) {
            sb.append(" has mutable type that can accumulate state");
        } else {
            sb.append(" can be reassigned at runtime");
        }

        sb.append(". This state is NOT shared across replicas.");

        return sb.toString();
    }

    private String buildRecommendation(FieldNode field, LeafTypeConfig config) {
        String typeName = field.extractTypeName();

        if (typeName != null && config.isCacheType(typeName)) {
            return "Move to a distributed cache (Redis, Hazelcast) for replica consistency";
        }

        if (typeName != null && config.isMutableCollectionType(typeName)) {
            return "Consider using a database or distributed data structure instead of in-memory collection";
        }

        if (!field.isFinal()) {
            return "Make field final if it shouldn't change, or externalize state to a shared store";
        }

        return "Externalize mutable state to a distributed store for replica consistency";
    }
}
