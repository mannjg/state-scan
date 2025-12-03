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
 * Detects usage of ThreadLocal storage.
 *
 * ThreadLocal can cause issues in replicated environments:
 * - State is thread-bound, not instance-bound
 * - With load balancer routing, requests may hit different threads
 * - Memory leaks if not properly cleaned up
 * - In reactive/async environments, context may be lost
 */
public class ThreadLocalDetector implements Detector {

    @Override
    public String id() {
        return "threadlocal";
    }

    @Override
    public String description() {
        return "Detects ThreadLocal storage usage";
    }

    @Override
    public List<Finding> detect(CallGraph graph, LeafTypeConfig config) {
        List<Finding> findings = new ArrayList<>();

        for (ClassNode cls : graph.allClasses()) {
            if (!cls.isProjectClass()) {
                continue;
            }

            for (FieldNode field : cls.fields()) {
                String typeName = field.extractTypeName();
                if (typeName == null) continue;

                if (config.isThreadLocalType(typeName)) {
                    findings.add(createFinding(cls, field, typeName));
                }
            }
        }

        return findings;
    }

    private Finding createFinding(ClassNode cls, FieldNode field, String typeName) {
        RiskLevel risk = field.isStatic() ? RiskLevel.HIGH : RiskLevel.MEDIUM;
        String variant = determineVariant(typeName);

        return Finding.builder()
                .className(cls.fqn())
                .fieldName(field.name())
                .fieldType(field.type())
                .stateType(StateType.IN_MEMORY)
                .riskLevel(risk)
                .pattern("ThreadLocal storage: " + variant)
                .description(buildDescription(cls, field, variant))
                .recommendation(buildRecommendation(variant))
                .detectorId(id())
                .sourceFile(cls.sourceFile())
                .build();
    }

    private String determineVariant(String typeName) {
        if (typeName.contains("InheritableThreadLocal")) {
            return "InheritableThreadLocal";
        }
        if (typeName.contains("FastThreadLocal")) {
            return "Netty FastThreadLocal";
        }
        return "ThreadLocal";
    }

    private String buildDescription(ClassNode cls, FieldNode field, String variant) {
        StringBuilder sb = new StringBuilder();
        sb.append("Class '").append(cls.simpleName()).append("' uses ").append(variant);
        sb.append(" in field '").append(field.name()).append("'. ");

        if (field.isStatic()) {
            sb.append("Static ThreadLocal is shared across the JVM but state is per-thread. ");
        }

        sb.append("This can cause issues with: ");
        sb.append("(1) async/reactive code where context is lost, ");
        sb.append("(2) thread pool reuse where old context leaks, ");
        sb.append("(3) load balancer routing where requests hit different threads.");

        return sb.toString();
    }

    private String buildRecommendation(String variant) {
        return String.format(
                "Consider alternatives to %s: " +
                        "(1) Pass context explicitly through method parameters, " +
                        "(2) Use request-scoped injection instead, " +
                        "(3) For reactive code, use context propagation (e.g., Reactor Context). " +
                        "If %s is necessary, ensure proper cleanup in finally blocks.",
                variant,
                variant
        );
    }
}
