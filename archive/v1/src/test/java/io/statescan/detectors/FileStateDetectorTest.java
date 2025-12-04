package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.*;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FileStateDetectorTest {

    private FileStateDetector detector;
    private LeafTypeConfig config;

    @BeforeEach
    void setUp() {
        detector = new FileStateDetector();
        config = LeafTypeConfig.loadDefault();
    }

    /**
     * Helper to create reachable classes set for a given class node.
     */
    private Set<String> reachableFrom(ClassNode... classes) {
        Set<String> reachable = new java.util.HashSet<>();
        for (ClassNode c : classes) {
            reachable.add(c.fqn());
        }
        return reachable;
    }

    @Test
    void id_returnsFileState() {
        assertThat(detector.id()).isEqualTo("file-state");
    }

    @Test
    void detect_findsFileOutputStreamField() {
        FieldNode field = FieldNode.builder()
                .name("output")
                .type("Ljava/io/FileOutputStream;")
                .isStatic(false)
                .isFinal(false)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.FileHandler")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).className()).isEqualTo("com.example.FileHandler");
        assertThat(findings.get(0).fieldName()).isEqualTo("output");
        assertThat(findings.get(0).riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void detect_findsRandomAccessFileField_asCritical() {
        FieldNode field = FieldNode.builder()
                .name("dataFile")
                .type("Ljava/io/RandomAccessFile;")
                .isStatic(true)
                .isFinal(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.DataStore")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(findings.get(0).pattern()).contains("RandomAccessFile");
    }

    @Test
    void detect_findsFileWriterField() {
        FieldNode field = FieldNode.builder()
                .name("logWriter")
                .type("Ljava/io/FileWriter;")
                .isStatic(false)
                .isFinal(false)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.Logger")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void detect_findsFileChannelField() {
        FieldNode field = FieldNode.builder()
                .name("channel")
                .type("Ljava/nio/channels/FileChannel;")
                .isStatic(false)
                .isFinal(false)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.FileProcessor")
                .fields(Set.of(field))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void detect_findsFilesWriteMethodInvocation() {
        MethodRef writeInvocation = new MethodRef(
                "java.nio.file.Files",
                "write",
                "(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;"
        );

        MethodNode method = MethodNode.builder()
                .name("saveData")
                .descriptor("()V")
                .invocations(Set.of(writeInvocation))
                .isPublic(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.DataSaver")
                .methods(Set.of(method))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).className()).isEqualTo("com.example.DataSaver");
        assertThat(findings.get(0).riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(findings.get(0).description()).contains("Files.write");
    }

    @Test
    void detect_findsFilesNewOutputStreamInvocation() {
        MethodRef invocation = new MethodRef(
                "java.nio.file.Files",
                "newOutputStream",
                "(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/OutputStream;"
        );

        MethodNode method = MethodNode.builder()
                .name("createStream")
                .descriptor("()V")
                .invocations(Set.of(invocation))
                .isPublic(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.StreamFactory")
                .methods(Set.of(method))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).description()).contains("Files.newOutputStream");
    }

    @Test
    void detect_findsFilesDeleteInvocation() {
        MethodRef deleteInvocation = new MethodRef(
                "java.nio.file.Files",
                "delete",
                "(Ljava/nio/file/Path;)V"
        );

        MethodNode method = MethodNode.builder()
                .name("cleanup")
                .descriptor("()V")
                .invocations(Set.of(deleteInvocation))
                .isPublic(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.TempFileCleaner")
                .methods(Set.of(method))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).hasSize(1);
    }

    @Test
    void detect_ignoresFilesReadOperations() {
        MethodRef readInvocation = new MethodRef(
                "java.nio.file.Files",
                "readAllBytes",
                "(Ljava/nio/file/Path;)[B"
        );

        MethodNode method = MethodNode.builder()
                .name("loadData")
                .descriptor("()V")
                .invocations(Set.of(readInvocation))
                .isPublic(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.DataLoader")
                .methods(Set.of(method))
                .isProjectClass(true)
                .build();

        CallGraph graph = CallGraph.builder()
                .addClass(classNode)
                .build();

        List<Finding> findings = detector.detect(graph, config, reachableFrom(classNode));

        assertThat(findings).isEmpty();
    }

    @Test
    void detect_ignoresFilesExistsCheck() {
        MethodRef existsInvocation = new MethodRef(
                "java.nio.file.Files",
                "exists",
                "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"
        );

        MethodNode method = MethodNode.builder()
                .name("checkFile")
                .descriptor("()Z")
                .invocations(Set.of(existsInvocation))
                .isPublic(true)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.FileChecker")
                .methods(Set.of(method))
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
                .name("output")
                .type("Ljava/io/FileOutputStream;")
                .isStatic(false)
                .isFinal(false)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("org.thirdparty.FileHandler")
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

    @Test
    void detect_ignoresNonFileTypes() {
        FieldNode field = FieldNode.builder()
                .name("data")
                .type("Ljava/util/HashMap;")
                .isStatic(false)
                .isFinal(false)
                .build();

        ClassNode classNode = ClassNode.builder()
                .fqn("com.example.DataStore")
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
