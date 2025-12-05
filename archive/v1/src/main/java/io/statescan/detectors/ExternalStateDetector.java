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
 * Detects external state dependencies (databases, message brokers).
 *
 * These are informational findings - external state is typically how you
 * should manage state in a replicated environment. However, it's useful
 * to document these dependencies for operational awareness.
 */
public class ExternalStateDetector implements Detector {

    @Override
    public String id() {
        return "external-state";
    }

    @Override
    public String description() {
        return "Documents external state dependencies (DB, messaging)";
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

                if (config.isExternalStateType(typeName)) {
                    findings.add(createFinding(cls, field, typeName));
                }
            }
        }

        return findings;
    }

    private Finding createFinding(ClassNode cls, FieldNode field, String typeName) {
        ExternalStateCategory category = categorize(typeName);

        return Finding.builder()
                .className(cls.fqn())
                .fieldName(field.name())
                .fieldType(field.type())
                .stateType(StateType.EXTERNAL)
                .riskLevel(category.riskLevel())
                .pattern(category.pattern())
                .description(buildDescription(cls, field, category))
                .recommendation(category.recommendation())
                .detectorId(id())
                .sourceFile(cls.sourceFile())
                .build();
    }

    private ExternalStateCategory categorize(String typeName) {
        // Database
        if (typeName.contains("DataSource") || typeName.contains("Connection") ||
                typeName.contains("sql")) {
            return ExternalStateCategory.DATABASE;
        }

        // JPA/Hibernate
        if (typeName.contains("EntityManager") || typeName.contains("Session") ||
                typeName.contains("hibernate")) {
            return ExternalStateCategory.JPA;
        }

        // Kafka
        if (typeName.contains("kafka")) {
            return ExternalStateCategory.KAFKA;
        }

        // JMS
        if (typeName.contains("jms")) {
            return ExternalStateCategory.JMS;
        }

        // RabbitMQ
        if (typeName.contains("rabbitmq")) {
            return ExternalStateCategory.RABBITMQ;
        }

        return ExternalStateCategory.OTHER;
    }

    private String buildDescription(ClassNode cls, FieldNode field, ExternalStateCategory category) {
        return String.format(
                "Class '%s' has %s dependency via field '%s'. %s",
                cls.simpleName(),
                category.name(),
                field.name(),
                category.note()
        );
    }

    private enum ExternalStateCategory {
        DATABASE(
                RiskLevel.LOW,
                "Database connection",
                "Ensure connection pooling is configured for horizontal scaling.",
                "This is the recommended way to manage shared state."
        ),
        JPA(
                RiskLevel.LOW,
                "JPA/Hibernate persistence",
                "Ensure EntityManager is request-scoped and connection pool is sized for replicas.",
                "JPA provides shared state across replicas via the database."
        ),
        KAFKA(
                RiskLevel.MEDIUM,
                "Kafka messaging",
                "Review partition strategy for horizontal scaling. Consider consumer group configuration.",
                "Kafka provides durable messaging but partition assignment affects scaling."
        ),
        JMS(
                RiskLevel.MEDIUM,
                "JMS messaging",
                "Ensure message broker supports clustered consumers for horizontal scaling.",
                "JMS provides messaging but competing consumers need careful configuration."
        ),
        RABBITMQ(
                RiskLevel.MEDIUM,
                "RabbitMQ messaging",
                "Configure queues appropriately for competing consumers across replicas.",
                "RabbitMQ supports horizontal scaling with proper queue configuration."
        ),
        OTHER(
                RiskLevel.INFO,
                "External state dependency",
                "Review this external dependency for horizontal scaling compatibility.",
                "External state generally supports horizontal scaling."
        );

        private final RiskLevel riskLevel;
        private final String pattern;
        private final String recommendation;
        private final String note;

        ExternalStateCategory(RiskLevel riskLevel, String pattern, String recommendation, String note) {
            this.riskLevel = riskLevel;
            this.pattern = pattern;
            this.recommendation = recommendation;
            this.note = note;
        }

        public RiskLevel riskLevel() { return riskLevel; }
        public String pattern() { return pattern; }
        public String recommendation() { return recommendation; }
        public String note() { return note; }
    }
}
