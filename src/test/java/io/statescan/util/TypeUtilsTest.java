package io.statescan.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeUtilsTest {

    @Test
    void cleanTypeName_convertsObjectDescriptor() {
        assertThat(TypeUtils.cleanTypeName("Lorg/apache/pulsar/Class;"))
                .isEqualTo("org.apache.pulsar.Class");
    }

    @Test
    void cleanTypeName_convertsNestedClassDescriptor() {
        assertThat(TypeUtils.cleanTypeName("Ljava/util/Map$Entry;"))
                .isEqualTo("java.util.Map$Entry");
    }

    @Test
    void cleanTypeName_convertsObjectArrayDescriptor() {
        assertThat(TypeUtils.cleanTypeName("[Ljava/lang/String;"))
                .isEqualTo("java.lang.String[]");
    }

    @Test
    void cleanTypeName_converts2DArrayDescriptor() {
        assertThat(TypeUtils.cleanTypeName("[[Ljava/lang/Object;"))
                .isEqualTo("java.lang.Object[][]");
    }

    @Test
    void cleanTypeName_convertsPrimitiveArrayDescriptor() {
        assertThat(TypeUtils.cleanTypeName("[I")).isEqualTo("int[]");
        assertThat(TypeUtils.cleanTypeName("[[Z")).isEqualTo("boolean[][]");
        assertThat(TypeUtils.cleanTypeName("[J")).isEqualTo("long[]");
    }

    @Test
    void cleanTypeName_passesAlreadyCleanName() {
        assertThat(TypeUtils.cleanTypeName("java.util.HashMap"))
                .isEqualTo("java.util.HashMap");
    }

    @Test
    void cleanTypeName_convertsSlashesToDots() {
        assertThat(TypeUtils.cleanTypeName("org/apache/Class"))
                .isEqualTo("org.apache.Class");
    }

    @Test
    void cleanTypeName_handlesNull() {
        assertThat(TypeUtils.cleanTypeName(null)).isNull();
    }

    @Test
    void cleanTypeName_handlesBlank() {
        assertThat(TypeUtils.cleanTypeName("")).isEqualTo("");
        assertThat(TypeUtils.cleanTypeName("   ")).isEqualTo("   ");
    }

    @Test
    void simpleClassName_extractsFromFqcn() {
        assertThat(TypeUtils.simpleClassName("java.util.HashMap"))
                .isEqualTo("HashMap");
    }

    @Test
    void simpleClassName_extractsNestedClass() {
        assertThat(TypeUtils.simpleClassName("java.util.Map$Entry"))
                .isEqualTo("Map$Entry");
    }

    @Test
    void simpleClassName_handlesNoPackage() {
        assertThat(TypeUtils.simpleClassName("String"))
                .isEqualTo("String");
    }

    @Test
    void simpleClassName_handlesNull() {
        assertThat(TypeUtils.simpleClassName(null)).isNull();
    }

    @Test
    void simpleTypeName_combinesBothOperations() {
        assertThat(TypeUtils.simpleTypeName("Ljava/util/HashMap;"))
                .isEqualTo("HashMap");
        assertThat(TypeUtils.simpleTypeName("[Ljava/lang/String;"))
                .isEqualTo("String[]");
    }
}
