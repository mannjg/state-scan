package io.statescan.graph;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CallGraphTest {

    @Test
    void builder_createsEmptyGraph() {
        CallGraph graph = CallGraph.builder().build();

        assertThat(graph.classCount()).isZero();
        assertThat(graph.allClasses()).isEmpty();
    }

    @Test
    void addClass_addsClassToGraph() {
        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        assertThat(graph.classCount()).isEqualTo(1);
        assertThat(graph.getClass("com.example.MyClass")).isPresent();
    }

    @Test
    void getClass_returnsEmptyForMissingClass() {
        CallGraph graph = CallGraph.builder().build();

        assertThat(graph.getClass("com.example.Missing")).isEmpty();
    }

    @Test
    void projectClasses_filtersNonProjectClasses() {
        ClassNode projectClass = ClassNode.builder()
                .fqn("com.example.MyClass")
                .isProjectClass(true)
                .build();

        ClassNode dependencyClass = ClassNode.builder()
                .fqn("org.external.TheirClass")
                .isProjectClass(false)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(projectClass)
                .addClass(dependencyClass)
                .build();

        assertThat(graph.classCount()).isEqualTo(2);
        assertThat(graph.projectClasses()).hasSize(1);
        assertThat(graph.projectClasses().iterator().next().fqn()).isEqualTo("com.example.MyClass");
    }

    @Test
    void buildHierarchy_createsSubtypeRelationships() {
        ClassNode parent = ClassNode.builder()
                .fqn("com.example.Parent")
                .isProjectClass(true)
                .build();

        ClassNode child = ClassNode.builder()
                .fqn("com.example.Child")
                .superclass("com.example.Parent")
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(parent)
                .addClass(child)
                .buildHierarchy()
                .build();

        assertThat(graph.directSubtypes("com.example.Parent")).containsExactly("com.example.Child");
        assertThat(graph.isSubtypeOf("com.example.Child", "com.example.Parent")).isTrue();
    }

    @Test
    void buildHierarchy_handlesInterfaces() {
        ClassNode iface = ClassNode.builder()
                .fqn("com.example.MyInterface")
                .isProjectClass(true)
                .isInterface(true)
                .build();

        ClassNode impl = ClassNode.builder()
                .fqn("com.example.MyImpl")
                .interfaces(Set.of("com.example.MyInterface"))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(iface)
                .addClass(impl)
                .buildHierarchy()
                .build();

        assertThat(graph.directSubtypes("com.example.MyInterface")).containsExactly("com.example.MyImpl");
        assertThat(graph.classesImplementing("com.example.MyInterface")).hasSize(1);
    }

    @Test
    void allSubtypes_returnsTransitiveSubtypes() {
        ClassNode grandparent = ClassNode.builder()
                .fqn("com.example.Grandparent")
                .isProjectClass(true)
                .build();

        ClassNode parent = ClassNode.builder()
                .fqn("com.example.Parent")
                .superclass("com.example.Grandparent")
                .isProjectClass(true)
                .build();

        ClassNode child = ClassNode.builder()
                .fqn("com.example.Child")
                .superclass("com.example.Parent")
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(grandparent)
                .addClass(parent)
                .addClass(child)
                .buildHierarchy()
                .build();

        Set<String> allSubtypes = graph.allSubtypes("com.example.Grandparent");
        assertThat(allSubtypes).containsExactlyInAnyOrder("com.example.Parent", "com.example.Child");
    }

    @Test
    void filterTo_keepsOnlySpecifiedClasses() {
        ClassNode class1 = ClassNode.builder()
                .fqn("com.example.Class1")
                .isProjectClass(true)
                .build();

        ClassNode class2 = ClassNode.builder()
                .fqn("com.example.Class2")
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(class1)
                .addClass(class2)
                .build();

        CallGraph filtered = graph.filterTo(Set.of("com.example.Class1"));

        assertThat(filtered.classCount()).isEqualTo(1);
        assertThat(filtered.getClass("com.example.Class1")).isPresent();
        assertThat(filtered.getClass("com.example.Class2")).isEmpty();
    }

    @Test
    void classesWithAnnotation_findsAnnotatedClasses() {
        ClassNode annotated = ClassNode.builder()
                .fqn("com.example.MySingleton")
                .annotations(Set.of("javax.inject.Singleton"))
                .isProjectClass(true)
                .build();

        ClassNode notAnnotated = ClassNode.builder()
                .fqn("com.example.RegularClass")
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(annotated)
                .addClass(notAnnotated)
                .build();

        Set<ClassNode> singletons = graph.classesWithAnnotation("javax.inject.Singleton");
        assertThat(singletons).hasSize(1);
        assertThat(singletons.iterator().next().fqn()).isEqualTo("com.example.MySingleton");
    }

    @Test
    void singletonClasses_findsClassesWithSingletonAnnotation() {
        ClassNode singleton = ClassNode.builder()
                .fqn("com.example.MySingleton")
                .annotations(Set.of("javax.inject.Singleton"))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(singleton)
                .build();

        assertThat(graph.singletonClasses()).hasSize(1);
    }

    @Test
    void classesWithStaticMutableFields_findsMatchingClasses() {
        FieldNode mutableField = FieldNode.builder()
                .name("CACHE")
                .type("Ljava/util/HashMap;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode withMutable = ClassNode.builder()
                .fqn("com.example.WithMutable")
                .fields(Set.of(mutableField))
                .isProjectClass(true)
                .build();

        ClassNode withoutMutable = ClassNode.builder()
                .fqn("com.example.WithoutMutable")
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(withMutable)
                .addClass(withoutMutable)
                .build();

        Set<ClassNode> withStaticMutable = graph.classesWithStaticMutableFields();
        assertThat(withStaticMutable).hasSize(1);
        assertThat(withStaticMutable.iterator().next().fqn()).isEqualTo("com.example.WithMutable");
    }
}
