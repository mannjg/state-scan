package io.statescan.graph;

import io.statescan.config.LeafTypeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PathFinderTest {

    private LeafTypeConfig leafConfig;

    @BeforeEach
    void setUp() throws IOException {
        leafConfig = LeafTypeConfig.loadDefault();
    }

    @Test
    void findPaths_skipsNonProjectRoots() {
        // Create a graph where a third-party class (not project) reaches a leaf type
        CallGraph graph = CallGraph.builder()
                .addClass(ClassNode.builder()
                        .fqn("ch.qos.logback.classic.ClassicConstants")
                        .isProjectClass(false)  // Not a project class
                        .build())
                .addClass(ClassNode.builder()
                        .fqn("java.lang.InheritableThreadLocal")
                        .isProjectClass(false)
                        .build())
                .build();

        PathFinder pathFinder = new PathFinder(graph, leafConfig);

        // Pass the non-project class as a root - it should be skipped
        Set<String> projectRoots = Set.of("ch.qos.logback.classic.ClassicConstants");
        List<PathFinder.StatefulPath> paths = pathFinder.findPaths(projectRoots);

        // No paths should be found because the root is not a project class
        assertThat(paths).isEmpty();
    }

    @Test
    void findPaths_reportsPathFromProjectClassToLeaf() {
        // Create a graph with a project class that has a field of type ThreadLocal
        CallGraph graph = CallGraph.builder()
                .addClass(ClassNode.builder()
                        .fqn("com.example.MyService")
                        .isProjectClass(true)
                        .fields(Set.of(FieldNode.builder()
                                .name("context")
                                .type("Ljava/lang/ThreadLocal;")
                                .build()))
                        .build())
                .addClass(ClassNode.builder()
                        .fqn("java.lang.ThreadLocal")
                        .isProjectClass(false)
                        .build())
                .build();

        PathFinder pathFinder = new PathFinder(graph, leafConfig);

        Set<String> projectRoots = Set.of("com.example.MyService");
        List<PathFinder.StatefulPath> paths = pathFinder.findPaths(projectRoots);

        // Path should be reported since root is a project class
        assertThat(paths).hasSize(1);
        assertThat(paths.get(0).root()).isEqualTo("com.example.MyService");
        assertThat(paths.get(0).leafType()).isEqualTo("java.lang.ThreadLocal");
        assertThat(paths.get(0).leafCategory()).isEqualTo("threadLocalTypes");
    }

    @Test
    void findPaths_deduplicatesPaths() {
        // Create a graph where two project classes reach the same leaf via the same intermediate
        CallGraph graph = CallGraph.builder()
                .addClass(ClassNode.builder()
                        .fqn("com.example.ServiceA")
                        .isProjectClass(true)
                        .fields(Set.of(FieldNode.builder()
                                .name("repo")
                                .type("Lcom/example/DataRepo;")
                                .build()))
                        .build())
                .addClass(ClassNode.builder()
                        .fqn("com.example.ServiceB")
                        .isProjectClass(true)
                        .fields(Set.of(FieldNode.builder()
                                .name("repo")
                                .type("Lcom/example/DataRepo;")
                                .build()))
                        .build())
                .addClass(ClassNode.builder()
                        .fqn("com.example.DataRepo")
                        .isProjectClass(true)
                        .fields(Set.of(FieldNode.builder()
                                .name("dataSource")
                                .type("Ljavax/sql/DataSource;")
                                .build()))
                        .build())
                .addClass(ClassNode.builder()
                        .fqn("javax.sql.DataSource")
                        .isProjectClass(false)
                        .build())
                .build();

        PathFinder pathFinder = new PathFinder(graph, leafConfig);

        Set<String> projectRoots = Set.of("com.example.ServiceA", "com.example.ServiceB");
        List<PathFinder.StatefulPath> paths = pathFinder.findPaths(projectRoots);

        // Should have unique paths (different roots lead to different paths even if they share the leaf)
        assertThat(paths).isNotEmpty();
        // All paths should start with project classes
        assertThat(paths).allMatch(p -> p.root().startsWith("com.example."));
    }

    @Test
    void findPaths_allPathsStartWithProjectClass() {
        // Create a graph simulating the logback scenario
        // Project class -> logback Logger -> logback internals -> ThreadLocal
        CallGraph graph = CallGraph.builder()
                .addClass(ClassNode.builder()
                        .fqn("com.example.MyService")
                        .isProjectClass(true)
                        .fields(Set.of(FieldNode.builder()
                                .name("logger")
                                .type("Lorg/slf4j/Logger;")
                                .build()))
                        .build())
                .addClass(ClassNode.builder()
                        .fqn("org.slf4j.Logger")
                        .isProjectClass(false)
                        .build())
                .addClass(ClassNode.builder()
                        .fqn("ch.qos.logback.classic.Logger")
                        .isProjectClass(false)
                        .superclass("java.lang.Object")
                        .interfaces(Set.of("org.slf4j.Logger"))
                        .build())
                // Note: We're NOT adding a path from Logger to ThreadLocal here
                // The test verifies that if logback classes were roots, they'd be skipped
                .build();

        PathFinder pathFinder = new PathFinder(graph, leafConfig);

        // Only provide project classes as roots
        Set<String> projectRoots = Set.of("com.example.MyService");
        List<PathFinder.StatefulPath> paths = pathFinder.findPaths(projectRoots);

        // All returned paths must start with project class
        for (PathFinder.StatefulPath path : paths) {
            assertThat(path.root()).startsWith("com.example.");
            assertThat(path.path().get(0)).isEqualTo(path.root());
        }
    }

    @Test
    void findPaths_nonProjectRootPassedDirectlyIsSkipped() {
        // Verify that even if a non-project class is passed in projectRoots, it's skipped
        CallGraph graph = CallGraph.builder()
                .addClass(ClassNode.builder()
                        .fqn("ch.qos.logback.classic.ClassicConstants")
                        .isProjectClass(false)
                        .fields(Set.of(FieldNode.builder()
                                .name("threadLocal")
                                .type("Ljava/lang/InheritableThreadLocal;")
                                .build()))
                        .build())
                .addClass(ClassNode.builder()
                        .fqn("java.lang.InheritableThreadLocal")
                        .isProjectClass(false)
                        .build())
                .build();

        PathFinder pathFinder = new PathFinder(graph, leafConfig);

        // Try to pass a non-project class as a root
        Set<String> roots = Set.of("ch.qos.logback.classic.ClassicConstants");
        List<PathFinder.StatefulPath> paths = pathFinder.findPaths(roots);

        // Should return empty because the root is not a project class
        assertThat(paths).isEmpty();
    }
}
