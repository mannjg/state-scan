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
 * Detects usage of in-memory caches that aren't shared across replicas.
 *
 * Local caches (Guava Cache, Caffeine, Ehcache in local mode) store data
 * per-instance, leading to stale data and cache inconsistency across replicas.
 */
public class CacheDetector implements Detector {

    @Override
    public String id() {
        return "cache";
    }

    @Override
    public String description() {
        return "Detects in-memory caches that aren't distributed";
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

                if (config.isCacheType(typeName)) {
                    findings.add(createFinding(cls, field, typeName));
                }
            }
        }

        return findings;
    }

    private Finding createFinding(ClassNode cls, FieldNode field, String typeName) {
        String cacheType = determineCacheType(typeName);

        return Finding.builder()
                .className(cls.fqn())
                .fieldName(field.name())
                .fieldType(field.type())
                .stateType(StateType.IN_MEMORY)
                .riskLevel(RiskLevel.CRITICAL)
                .pattern("In-memory cache: " + cacheType)
                .description(buildDescription(cls, field, cacheType))
                .recommendation(buildRecommendation(cacheType))
                .detectorId(id())
                .sourceFile(cls.sourceFile())
                .build();
    }

    private String determineCacheType(String typeName) {
        if (typeName.contains("guava") || typeName.contains("google.common.cache")) {
            return "Guava Cache";
        }
        if (typeName.contains("caffeine")) {
            return "Caffeine Cache";
        }
        if (typeName.contains("ehcache")) {
            return "Ehcache (local)";
        }
        if (typeName.contains("springframework.cache")) {
            return "Spring Cache";
        }
        return "In-memory Cache";
    }

    private String buildDescription(ClassNode cls, FieldNode field, String cacheType) {
        return String.format(
                "Class '%s' uses %s in field '%s'. " +
                        "This cache is local to each replica and will have different data in each instance. " +
                        "Cache invalidations won't propagate across replicas.",
                cls.simpleName(),
                cacheType,
                field.name()
        );
    }

    private String buildRecommendation(String cacheType) {
        return String.format(
                "Replace %s with a distributed cache solution: " +
                        "Redis, Hazelcast, or configure %s with a distributed backend. " +
                        "Ensure cache invalidation propagates to all replicas.",
                cacheType,
                cacheType
        );
    }
}
