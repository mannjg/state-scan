package io.statescan.report;

import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.StateType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClassAggregationTest {

    @Test
    void aggregate_groupsFindingsByClass() {
        List<Finding> findings = List.of(
                createFinding("com.example.ServiceA", "field1", "Map", RiskLevel.HIGH),
                createFinding("com.example.ServiceA", "field2", "List", RiskLevel.MEDIUM),
                createFinding("com.example.ServiceB", "cache", "Cache", RiskLevel.CRITICAL)
        );

        List<ClassAggregation.ClassEntry> result = ClassAggregation.aggregate(findings);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ClassAggregation.ClassEntry::className)
                .containsExactlyInAnyOrder("com.example.ServiceA", "com.example.ServiceB");
    }

    @Test
    void aggregate_countsFindingsPerClass() {
        List<Finding> findings = List.of(
                createFinding("com.example.Service", "field1", "Map", RiskLevel.HIGH),
                createFinding("com.example.Service", "field2", "List", RiskLevel.HIGH),
                createFinding("com.example.Service", "field3", "Set", RiskLevel.HIGH)
        );

        List<ClassAggregation.ClassEntry> result = ClassAggregation.aggregate(findings);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).findingCount()).isEqualTo(3);
        assertThat(result.get(0).fields()).hasSize(3);
    }

    @Test
    void aggregate_extractsSimpleClassName() {
        List<Finding> findings = List.of(
                createFinding("com.example.nested.DeepClass", "field", "Map", RiskLevel.HIGH)
        );

        List<ClassAggregation.ClassEntry> result = ClassAggregation.aggregate(findings);

        assertThat(result.get(0).simpleClassName()).isEqualTo("DeepClass");
    }

    @Test
    void aggregate_determinesHighestRiskLevel() {
        List<Finding> findings = List.of(
                createFinding("com.example.Service", "field1", "Map", RiskLevel.LOW),
                createFinding("com.example.Service", "field2", "List", RiskLevel.CRITICAL),
                createFinding("com.example.Service", "field3", "Set", RiskLevel.MEDIUM)
        );

        List<ClassAggregation.ClassEntry> result = ClassAggregation.aggregate(findings);

        assertThat(result.get(0).highestRisk()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void aggregate_sortsByRiskLevelThenFindingCount() {
        List<Finding> findings = List.of(
                // Class with 3 HIGH findings
                createFinding("com.example.HighRisk", "f1", "Map", RiskLevel.HIGH),
                createFinding("com.example.HighRisk", "f2", "List", RiskLevel.HIGH),
                createFinding("com.example.HighRisk", "f3", "Set", RiskLevel.HIGH),
                // Class with 1 CRITICAL finding
                createFinding("com.example.CriticalRisk", "cache", "Cache", RiskLevel.CRITICAL),
                // Class with 2 MEDIUM findings
                createFinding("com.example.MediumRisk", "x", "X", RiskLevel.MEDIUM),
                createFinding("com.example.MediumRisk", "y", "Y", RiskLevel.MEDIUM)
        );

        List<ClassAggregation.ClassEntry> result = ClassAggregation.aggregate(findings);

        // Should be sorted: CRITICAL first, then HIGH, then MEDIUM
        assertThat(result).extracting(ClassAggregation.ClassEntry::className)
                .containsExactly("com.example.CriticalRisk", "com.example.HighRisk", "com.example.MediumRisk");
    }

    @Test
    void aggregate_deduplicatesFieldsByName() {
        List<Finding> findings = List.of(
                // Same field reported twice (e.g., by different detectors)
                createFinding("com.example.Service", "cache", "Map", RiskLevel.HIGH),
                createFinding("com.example.Service", "cache", "Map", RiskLevel.CRITICAL)
        );

        List<ClassAggregation.ClassEntry> result = ClassAggregation.aggregate(findings);

        // Should have 2 findings but only 1 unique field
        assertThat(result.get(0).findingCount()).isEqualTo(2);
        assertThat(result.get(0).fields()).hasSize(1);
    }

    @Test
    void aggregate_cleansFieldTypeDescriptors() {
        List<Finding> findings = List.of(
                Finding.builder()
                        .className("com.example.Service")
                        .fieldName("map")
                        .fieldType("Ljava/util/HashMap;")  // Bytecode descriptor
                        .pattern("Static mutable field")
                        .stateType(StateType.IN_MEMORY)
                        .riskLevel(RiskLevel.HIGH)
                        .build()
        );

        List<ClassAggregation.ClassEntry> result = ClassAggregation.aggregate(findings);

        // Field type should be cleaned to simple name
        assertThat(result.get(0).fields().get(0).fieldType()).isEqualTo("HashMap");
    }

    @Test
    void aggregate_extractsShortPatternDescriptions() {
        List<Finding> findings = List.of(
                createFindingWithPattern("com.example.A", "f1", "Static mutable field: HashMap with concurrent access"),
                createFindingWithPattern("com.example.B", "f2", "Static ThreadLocal field"),
                createFindingWithPattern("com.example.C", "f3", "Singleton with mutable instance field")
        );

        List<ClassAggregation.ClassEntry> result = ClassAggregation.aggregate(findings);

        assertThat(result).extracting(e -> e.fields().get(0).pattern())
                .containsExactlyInAnyOrder("static mutable", "ThreadLocal", "singleton mutable");
    }

    @Test
    void aggregate_returnsEmptyForEmptyInput() {
        List<ClassAggregation.ClassEntry> result = ClassAggregation.aggregate(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void aggregate_handlesNullFieldNames() {
        List<Finding> findings = List.of(
                Finding.builder()
                        .className("com.example.Service")
                        .fieldName(null)  // No field name
                        .pattern("Some pattern")
                        .stateType(StateType.IN_MEMORY)
                        .riskLevel(RiskLevel.HIGH)
                        .build()
        );

        List<ClassAggregation.ClassEntry> result = ClassAggregation.aggregate(findings);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fields()).isEmpty();  // No fields extracted
        assertThat(result.get(0).findingCount()).isEqualTo(1);  // But finding is counted
    }

    private Finding createFinding(String className, String fieldName, String fieldType, RiskLevel risk) {
        return Finding.builder()
                .className(className)
                .fieldName(fieldName)
                .fieldType("L" + fieldType.replace('.', '/') + ";")
                .pattern("Static mutable field")
                .stateType(StateType.IN_MEMORY)
                .riskLevel(risk)
                .build();
    }

    private Finding createFindingWithPattern(String className, String fieldName, String pattern) {
        return Finding.builder()
                .className(className)
                .fieldName(fieldName)
                .fieldType("Ljava/util/Map;")
                .pattern(pattern)
                .stateType(StateType.IN_MEMORY)
                .riskLevel(RiskLevel.HIGH)
                .build();
    }
}
