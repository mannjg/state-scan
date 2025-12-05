package io.statescan.graph;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldNodeTest {

    @Test
    void builder_createsFieldNode() {
        FieldNode field = FieldNode.builder()
                .name("myField")
                .type("Ljava/lang/String;")
                .isStatic(false)
                .isFinal(true)
                .build();

        assertThat(field.name()).isEqualTo("myField");
        assertThat(field.type()).isEqualTo("Ljava/lang/String;");
        assertThat(field.isStatic()).isFalse();
        assertThat(field.isFinal()).isTrue();
    }

    @Test
    void builder_requiresName() {
        assertThatThrownBy(() -> FieldNode.builder()
                .type("Ljava/lang/String;")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractTypeName_extractsObjectType() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("Ljava/util/HashMap;")
                .build();

        assertThat(field.extractTypeName()).isEqualTo("java.util.HashMap");
    }

    @Test
    void extractTypeName_returnsNullForPrimitive() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("I")
                .build();

        assertThat(field.extractTypeName()).isNull();
    }

    @Test
    void extractTypeName_handlesArrays() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("[Ljava/lang/String;")
                .build();

        assertThat(field.extractTypeName()).isEqualTo("java.lang.String");
    }

    @Test
    void extractTypeName_handlesMultiDimensionalArrays() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("[[Ljava/lang/String;")
                .build();

        assertThat(field.extractTypeName()).isEqualTo("java.lang.String");
    }

    @Test
    void isPotentiallyMutable_trueForNonFinal() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("Ljava/lang/String;")
                .isFinal(false)
                .build();

        assertThat(field.isPotentiallyMutable()).isTrue();
    }

    @Test
    void isPotentiallyMutable_trueForFinalMap() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("Ljava/util/HashMap;")
                .isFinal(true)
                .build();

        assertThat(field.isPotentiallyMutable()).isTrue();
    }

    @Test
    void isPotentiallyMutable_trueForFinalList() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("Ljava/util/ArrayList;")
                .isFinal(true)
                .build();

        assertThat(field.isPotentiallyMutable()).isTrue();
    }

    @Test
    void isPotentiallyMutable_trueForAtomic() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("Ljava/util/concurrent/atomic/AtomicInteger;")
                .isFinal(true)
                .build();

        assertThat(field.isPotentiallyMutable()).isTrue();
    }

    @Test
    void isPotentiallyMutable_falseForFinalString() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("Ljava/lang/String;")
                .isFinal(true)
                .build();

        assertThat(field.isPotentiallyMutable()).isFalse();
    }

    @Test
    void isStaticMutable_trueForStaticMutableField() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("Ljava/util/HashMap;")
                .isStatic(true)
                .isFinal(true)
                .build();

        assertThat(field.isStaticMutable()).isTrue();
    }

    @Test
    void isStaticMutable_falseForInstanceField() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("Ljava/util/HashMap;")
                .isStatic(false)
                .isFinal(true)
                .build();

        assertThat(field.isStaticMutable()).isFalse();
    }

    @Test
    void isConstant_trueForStaticFinalPrimitive() {
        FieldNode field = FieldNode.builder()
                .name("MAX_SIZE")
                .type("I")
                .isStatic(true)
                .isFinal(true)
                .build();

        assertThat(field.isConstant()).isTrue();
    }

    @Test
    void isConstant_trueForStaticFinalString() {
        FieldNode field = FieldNode.builder()
                .name("NAME")
                .type("Ljava/lang/String;")
                .isStatic(true)
                .isFinal(true)
                .build();

        assertThat(field.isConstant()).isTrue();
    }

    @Test
    void isConstant_falseForStaticFinalCollection() {
        FieldNode field = FieldNode.builder()
                .name("ITEMS")
                .type("Ljava/util/List;")
                .isStatic(true)
                .isFinal(true)
                .build();

        assertThat(field.isConstant()).isFalse();
    }

    @Test
    void isConstant_falseForNonStatic() {
        FieldNode field = FieldNode.builder()
                .name("name")
                .type("Ljava/lang/String;")
                .isStatic(false)
                .isFinal(true)
                .build();

        assertThat(field.isConstant()).isFalse();
    }

    @Test
    void isLogger_trueForSlf4jLogger() {
        FieldNode field = FieldNode.builder()
                .name("LOGGER")
                .type("Lorg/slf4j/Logger;")
                .isStatic(true)
                .isFinal(true)
                .build();

        assertThat(field.isLogger()).isTrue();
    }

    @Test
    void isLogger_trueForLog4jLogger() {
        FieldNode field = FieldNode.builder()
                .name("log")
                .type("Lorg/apache/logging/log4j/Logger;")
                .isStatic(true)
                .isFinal(true)
                .build();

        assertThat(field.isLogger()).isTrue();
    }

    @Test
    void isLogger_falseForNonLogger() {
        FieldNode field = FieldNode.builder()
                .name("cache")
                .type("Ljava/util/HashMap;")
                .isStatic(true)
                .isFinal(true)
                .build();

        assertThat(field.isLogger()).isFalse();
    }

    @Test
    void readableType_convertsDescriptor() {
        FieldNode field = FieldNode.builder()
                .name("field")
                .type("Ljava/util/HashMap;")
                .build();

        assertThat(field.readableType()).isEqualTo("java.util.HashMap");
    }

    @Test
    void readableType_handlesPrimitives() {
        FieldNode intField = FieldNode.builder()
                .name("field")
                .type("I")
                .build();

        assertThat(intField.readableType()).isEqualTo("int");
    }
}
