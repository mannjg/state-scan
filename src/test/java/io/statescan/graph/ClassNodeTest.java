package io.statescan.graph;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassNodeTest {

    @Test
    void builder_createsClassNode() {
        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .superclass("java.lang.Object")
                .isProjectClass(true)
                .build();

        assertThat(classNode.fqn()).isEqualTo("com.example.MyClass");
        assertThat(classNode.superclass()).isEqualTo("java.lang.Object");
        assertThat(classNode.isProjectClass()).isTrue();
    }

    @Test
    void builder_requiresFqn() {
        assertThatThrownBy(() -> ClassNode.builder()
                .superclass("java.lang.Object")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simpleName_extractsClassName() {
        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .build();

        assertThat(classNode.simpleName()).isEqualTo("MyClass");
    }

    @Test
    void simpleName_handlesNoPackage() {
        ClassNode classNode = ClassNode.builder()
                .fqn("MyClass")
                .build();

        assertThat(classNode.simpleName()).isEqualTo("MyClass");
    }

    @Test
    void packageName_extractsPackage() {
        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .build();

        assertThat(classNode.packageName()).isEqualTo("com.example");
    }

    @Test
    void packageName_returnsEmptyForNoPackage() {
        ClassNode classNode = ClassNode.builder()
                .fqn("MyClass")
                .build();

        assertThat(classNode.packageName()).isEmpty();
    }

    @Test
    void hasAnnotation_matchesExactAnnotation() {
        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .annotations(Set.of("javax.inject.Singleton"))
                .build();

        assertThat(classNode.hasAnnotation("javax.inject.Singleton")).isTrue();
        assertThat(classNode.hasAnnotation("Singleton")).isTrue();
        assertThat(classNode.hasAnnotation("Component")).isFalse();
    }

    @Test
    void hasSingletonAnnotation_detectsSingletonAnnotations() {
        ClassNode singleton = ClassNode.builder()
                .fqn("com.example.MySingleton")
                .annotations(Set.of("javax.inject.Singleton"))
                .build();

        ClassNode appScoped = ClassNode.builder()
                .fqn("com.example.MyAppScoped")
                .annotations(Set.of("javax.enterprise.context.ApplicationScoped"))
                .build();

        ClassNode regular = ClassNode.builder()
                .fqn("com.example.Regular")
                .build();

        assertThat(singleton.hasSingletonAnnotation()).isTrue();
        assertThat(appScoped.hasSingletonAnnotation()).isTrue();
        assertThat(regular.hasSingletonAnnotation()).isFalse();
    }

    @Test
    void isGuiceModule_detectsAbstractModule() {
        ClassNode module = ClassNode.builder()
                .fqn("com.example.MyModule")
                .superclass("com.google.inject.AbstractModule")
                .build();

        assertThat(module.isGuiceModule()).isTrue();
    }

    @Test
    void isRestResource_detectsPathAnnotation() {
        ClassNode resource = ClassNode.builder()
                .fqn("com.example.MyResource")
                .annotations(Set.of("javax.ws.rs.Path"))
                .build();

        assertThat(resource.isRestResource()).isTrue();
    }

    @Test
    void isRestResource_detectsRestController() {
        ClassNode controller = ClassNode.builder()
                .fqn("com.example.MyController")
                .annotations(Set.of("org.springframework.web.bind.annotation.RestController"))
                .build();

        assertThat(controller.isRestResource()).isTrue();
    }

    @Test
    void findField_findsExistingField() {
        FieldNode field = FieldNode.builder()
                .name("myField")
                .type("Ljava/lang/String;")
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(field))
                .build();

        assertThat(classNode.findField("myField")).isPresent();
        assertThat(classNode.findField("nonexistent")).isEmpty();
    }

    @Test
    void staticMutableFields_returnsMutableStaticFields() {
        FieldNode mutableStatic = FieldNode.builder()
                .name("CACHE")
                .type("Ljava/util/HashMap;")
                .isStatic(true)
                .isFinal(true)
                .build();

        FieldNode immutableStatic = FieldNode.builder()
                .name("CONSTANT")
                .type("Ljava/lang/String;")
                .isStatic(true)
                .isFinal(true)
                .build();

        FieldNode mutableInstance = FieldNode.builder()
                .name("data")
                .type("Ljava/util/HashMap;")
                .isStatic(false)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(mutableStatic, immutableStatic, mutableInstance))
                .build();

        Set<FieldNode> staticMutable = classNode.staticMutableFields();
        assertThat(staticMutable).hasSize(1);
        assertThat(staticMutable.iterator().next().name()).isEqualTo("CACHE");
    }

    @Test
    void mutableInstanceFields_returnsMutableNonStaticFields() {
        FieldNode mutableInstance = FieldNode.builder()
                .name("data")
                .type("Ljava/util/HashMap;")
                .isStatic(false)
                .isFinal(true)
                .build();

        FieldNode mutableStatic = FieldNode.builder()
                .name("CACHE")
                .type("Ljava/util/HashMap;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.MyClass")
                .fields(Set.of(mutableInstance, mutableStatic))
                .build();

        Set<FieldNode> mutableFields = classNode.mutableInstanceFields();
        assertThat(mutableFields).hasSize(1);
        assertThat(mutableFields.iterator().next().name()).isEqualTo("data");
    }
}
