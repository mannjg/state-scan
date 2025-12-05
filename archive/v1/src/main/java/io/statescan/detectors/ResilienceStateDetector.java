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
import java.util.Set;

/**
 * Detects resilience patterns (circuit breakers, retry state, bulkheads).
 *
 * These components maintain state that is NOT shared across replicas:
 * - Circuit breaker state (open/closed/half-open) differs per replica
 * - Retry counters are per-replica
 * - Rate limiters don't coordinate across replicas
 *
 * This can lead to inconsistent behavior where one replica has an open
 * circuit breaker while others continue to hammer a failing service.
 */
public class ResilienceStateDetector implements Detector {

    @Override
    public String id() {
        return "resilience";
    }

    @Override
    public String description() {
        return "Detects resilience patterns with per-instance state";
    }

    @Override
    public List<Finding> detect(CallGraph graph, LeafTypeConfig config, Set<String> reachableClasses) {
        List<Finding> findings = new ArrayList<>();

        for (ClassNode cls : graph.allClasses()) {
            // Only analyze classes reachable from project roots
            if (!reachableClasses.contains(cls.fqn())) {
                continue;
            }

            for (FieldNode field : cls.fields()) {
                String typeName = field.extractTypeName();
                if (typeName == null) continue;

                if (config.isResilienceType(typeName)) {
                    findings.add(createFinding(cls, field, typeName));
                }
            }
        }

        return findings;
    }

    private Finding createFinding(ClassNode cls, FieldNode field, String typeName) {
        ResilienceType type = categorize(typeName);

        return Finding.builder()
                .className(cls.fqn())
                .fieldName(field.name())
                .fieldType(field.type())
                .stateType(StateType.RESILIENCE)
                .riskLevel(type.riskLevel())
                .pattern(type.pattern())
                .description(buildDescription(cls, field, type))
                .recommendation(type.recommendation())
                .detectorId(id())
                .sourceFile(cls.sourceFile())
                .build();
    }

    private ResilienceType categorize(String typeName) {
        if (typeName.contains("CircuitBreaker")) {
            return ResilienceType.CIRCUIT_BREAKER;
        }
        if (typeName.contains("Retry")) {
            return ResilienceType.RETRY;
        }
        if (typeName.contains("Bulkhead")) {
            return ResilienceType.BULKHEAD;
        }
        if (typeName.contains("RateLimiter")) {
            return ResilienceType.RATE_LIMITER;
        }
        if (typeName.contains("Hystrix")) {
            return ResilienceType.HYSTRIX;
        }
        return ResilienceType.OTHER;
    }

    private String buildDescription(ClassNode cls, FieldNode field, ResilienceType type) {
        return String.format(
                "Class '%s' uses %s in field '%s'. %s " +
                        "Each replica maintains its own state independently.",
                cls.simpleName(),
                type.name(),
                field.name(),
                type.stateDescription()
        );
    }

    private enum ResilienceType {
        CIRCUIT_BREAKER(
                RiskLevel.HIGH,
                "Circuit Breaker",
                "Circuit breaker state (open/closed/half-open) is per-replica.",
                "Consider using a distributed circuit breaker or accepting that each replica " +
                        "will independently detect failures. In practice, this often works as " +
                        "each replica will eventually open its circuit if the backend is failing."
        ),
        RETRY(
                RiskLevel.MEDIUM,
                "Retry mechanism",
                "Retry counters are tracked per-replica.",
                "Per-replica retries are usually acceptable. Be aware that total retry load " +
                        "on a failing service is multiplied by replica count."
        ),
        BULKHEAD(
                RiskLevel.HIGH,
                "Bulkhead/Isolation",
                "Bulkhead limits are enforced per-replica only.",
                "Total concurrent calls to a service = bulkhead_limit * replica_count. " +
                        "Adjust bulkhead sizes when scaling, or use distributed semaphores."
        ),
        RATE_LIMITER(
                RiskLevel.HIGH,
                "Rate Limiter",
                "Rate limits are enforced per-replica only.",
                "Actual rate = configured_rate * replica_count. Consider using a distributed " +
                        "rate limiter (Redis-based) or adjust per-replica limits when scaling."
        ),
        HYSTRIX(
                RiskLevel.HIGH,
                "Hystrix (legacy)",
                "Hystrix circuit breaker state is per-replica.",
                "Hystrix is deprecated. Consider migrating to Resilience4j. " +
                        "Circuit breaker state is not shared across replicas."
        ),
        OTHER(
                RiskLevel.MEDIUM,
                "Resilience component",
                "This resilience component maintains local state.",
                "Review how this component's state affects horizontal scaling."
        );

        private final RiskLevel riskLevel;
        private final String pattern;
        private final String stateDescription;
        private final String recommendation;

        ResilienceType(RiskLevel riskLevel, String pattern, String stateDescription, String recommendation) {
            this.riskLevel = riskLevel;
            this.pattern = pattern;
            this.stateDescription = stateDescription;
            this.recommendation = recommendation;
        }

        public RiskLevel riskLevel() { return riskLevel; }
        public String pattern() { return pattern; }
        public String stateDescription() { return stateDescription; }
        public String recommendation() { return recommendation; }
    }
}
