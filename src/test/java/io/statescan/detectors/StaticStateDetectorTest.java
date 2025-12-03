package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.CallGraph;
import io.statescan.graph.ClassNode;
import io.statescan.graph.FieldNode;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StaticStateDetectorTest {

    private StaticStateDetector detector;
    private LeafTypeConfig config;

    @BeforeEach
    void setUp() {
        detector = new StaticStateDetector();
        config = LeafTypeConfig.loadDefault();
    }

    @Test
    void id_returnsStaticState() {
        assertThat(detector.id()).isEqualTo("static-state");
    }

    @Test
    void detect_findsMutableStaticMap() {
        FieldNode field = FieldNode.builder()
                .name("CACHE")
                .type("Ljava/util/HashMap;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).className()).isEqualTo("com.example.MyClass");
        assertThat(findings.get(0).fieldName()).isEqualTo("CACHE");
        assertThat(findings.get(0).riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void detect_findsMutableStaticList() {
        FieldNode field = FieldNode.builder()
                .name("ITEMS")
                .type("Ljava/util/ArrayList;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).fieldName()).isEqualTo("ITEMS");
    }

    @Test
    void detect_findsNonFinalStaticField() {
        FieldNode field = FieldNode.builder()
                .name("counter")
                .type("I") // int
                .isStatic(true)
                .isFinal(false)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).riskLevel()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void detect_ignoresStaticFinalString() {
        FieldNode field = FieldNode.builder()
                .name("CONSTANT")
                .type("Ljava/lang/String;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config);

        assertThat(findings).isEmpty();
    }

    @Test
    void detect_ignoresStaticFinalPrimitive() {
        FieldNode field = FieldNode.builder()
                .name("MAX_SIZE")
                .type("I") // int
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config);

        assertThat(findings).isEmpty();
    }

    @Test
    void detect_ignoresNonProjectClasses() {
        FieldNode field = FieldNode.builder()
                .name("CACHE")
                .type("Ljava/util/HashMap;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("org.thirdparty.TheirClass")
                .fields(Set.of(field))
                .isProjectClass(false)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config);

        assertThat(findings).isEmpty();
    }

    @Test
    void detect_ignoresLoggers() {
        FieldNode field = FieldNode.builder()
                .name("LOGGER")
                .type("Lorg/slf4j/Logger;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config);

        assertThat(findings).isEmpty();
    }

    @Test
    void detect_findsAtomicInteger() {
        FieldNode field = FieldNode.builder()
                .name("COUNTER")
                .type("Ljava/util/concurrent/atomic/AtomicInteger;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).fieldName()).isEqualTo("COUNTER");
    }

    @Test
    void detect_ignoresEnumValues() {
        FieldNode field = FieldNode.builder()
                .name("$VALUES")
                .type("[Lcom/example/MyEnum;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyEnum")
                .fields(Set.of(field))
                .isProjectClass(true)
                .isEnum(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config);

        assertThat(findings).isEmpty();
    }

    @Test
    void detect_ignoresInstanceFields() {
        FieldNode field = FieldNode.builder()
                .name("cache")
                .type("Ljava/util/HashMap;")
                .isStatic(false)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config);

        // StaticStateDetector only looks at static fields
        assertThat(findings).isEmpty();
    }
}
