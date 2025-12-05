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

class ThreadLocalDetectorTest {

    private ThreadLocalDetector detector;
    private LeafTypeConfig config;

    @BeforeEach
    void setUp() {
        detector = new ThreadLocalDetector();
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
    void id_returnsThreadLocal() {
        assertThat(detector.id()).isEqualTo("threadlocal");
    }

    @Test
    void detect_findsStaticThreadLocal() {
        FieldNode field = FieldNode.builder()
                .name("CONTEXT")
                .type("Ljava/lang/ThreadLocal;")
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

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).className()).isEqualTo("com.example.MyClass");
        assertThat(findings.get(0).fieldName()).isEqualTo("CONTEXT");
    }

    @Test
    void detect_findsInstanceThreadLocal() {
        FieldNode field = FieldNode.builder()
                .name("userContext")
                .type("Ljava/lang/ThreadLocal;")
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

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
    }

    @Test
    void detect_findsInheritableThreadLocal() {
        FieldNode field = FieldNode.builder()
                .name("INHERITABLE")
                .type("Ljava/lang/InheritableThreadLocal;")
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

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
    }

    @Test
    void detect_ignoresNonThreadLocalFields() {
        FieldNode field = FieldNode.builder()
                .name("cache")
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

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).isEmpty();
    }

    @Test
    void detect_ignoresNonReachableClasses() {
        FieldNode field = FieldNode.builder()
                .name("CONTEXT")
                .type("Ljava/lang/ThreadLocal;")
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

        // Class is in graph but NOT in reachable set - should be ignored
        List<Finding> findings = detector.detect(graph, config, Set.of());

        assertThat(findings).isEmpty();
    }
}
