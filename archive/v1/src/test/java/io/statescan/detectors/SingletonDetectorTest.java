package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.CallGraph;
import io.statescan.graph.ClassNode;
import io.statescan.graph.FieldNode;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SingletonDetectorTest {

    private SingletonDetector detector;
    private LeafTypeConfig config;

    @BeforeEach
    void setUp() {
        detector = new SingletonDetector();
        config = LeafTypeConfig.loadDefault();
    }

    /**
     * Helper to create reachable classes set for given class nodes.
     */
    private Set<String> reachableFrom(ClassNode... classes) {
        Set<String> reachable = new HashSet<>();
        for (ClassNode c : classes) {
            reachable.add(c.fqn());
        }
        return reachable;
    }

    @Test
    void id_returnsSingleton() {
        assertThat(detector.id()).isEqualTo("singleton");
    }

    @Test
    void detect_findsMutableFieldInSingleton() {
        FieldNode field = FieldNode.builder()
                .name("cache")
                .type("Ljava/util/HashMap;")
                .isStatic(false)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MySingleton")
                .annotations(Set.of("javax.inject.Singleton"))
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).className()).isEqualTo("com.example.MySingleton");
        assertThat(findings.get(0).fieldName()).isEqualTo("cache");
    }

    @Test
    void detect_findsMutableFieldInApplicationScoped() {
        FieldNode field = FieldNode.builder()
                .name("items")
                .type("Ljava/util/ArrayList;")
                .isStatic(false)
                .isFinal(false)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyService")
                .annotations(Set.of("jakarta.enterprise.context.ApplicationScoped"))
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).fieldName()).isEqualTo("items");
    }

    @Test
    void detect_ignoresImmutableFieldsInSingleton() {
        FieldNode stringField = FieldNode.builder()
                .name("name")
                .type("Ljava/lang/String;")
                .isStatic(false)
                .isFinal(true)
                .build();

        FieldNode intField = FieldNode.builder()
                .name("id")
                .type("I")
                .isStatic(false)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MySingleton")
                .annotations(Set.of("javax.inject.Singleton"))
                .fields(Set.of(stringField, intField))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).isEmpty();
    }

    @Test
    void detect_ignoresNonSingletonClasses() {
        FieldNode field = FieldNode.builder()
                .name("cache")
                .type("Ljava/util/HashMap;")
                .isStatic(false)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.RegularClass")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        // SingletonDetector only checks classes with singleton annotations
        assertThat(findings).isEmpty();
    }

    @Test
    void detect_findsMultipleMutableFields() {
        FieldNode field1 = FieldNode.builder()
                .name("cache")
                .type("Ljava/util/HashMap;")
                .isStatic(false)
                .isFinal(true)
                .build();

        FieldNode field2 = FieldNode.builder()
                .name("items")
                .type("Ljava/util/ArrayList;")
                .isStatic(false)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MySingleton")
                .annotations(Set.of("javax.inject.Singleton"))
                .fields(Set.of(field1, field2))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(2);
    }

    @Test
    void detect_ignoresStaticFields() {
        // SingletonDetector is for instance fields in singleton-annotated classes
        FieldNode field = FieldNode.builder()
                .name("STATIC_CACHE")
                .type("Ljava/util/HashMap;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MySingleton")
                .annotations(Set.of("javax.inject.Singleton"))
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        // Static fields are handled by StaticStateDetector, not SingletonDetector
        assertThat(findings).isEmpty();
    }

    @Test
    void detect_ignoresLoggerInSingleton() {
        FieldNode field = FieldNode.builder()
                .name("logger")
                .type("Lorg/slf4j/Logger;")
                .isStatic(false)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MySingleton")
                .annotations(Set.of("javax.inject.Singleton"))
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).isEmpty();
    }
}
