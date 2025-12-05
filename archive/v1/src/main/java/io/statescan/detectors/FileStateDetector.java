package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.CallGraph;
import io.statescan.graph.ClassNode;
import io.statescan.graph.FieldNode;
import io.statescan.graph.MethodNode;
import io.statescan.graph.MethodRef;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.StateType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detects file I/O operations that indicate local file system state.
 *
 * Local file writes are problematic in horizontally scaled environments because:
 * - Files are not shared across replicas
 * - Data written by one replica is invisible to others
 * - Can cause data inconsistency or loss
 */
public class FileStateDetector implements Detector {

    // File write methods on java.nio.file.Files that indicate state mutation
    private static final Set<String> FILES_WRITE_METHODS = Set.of(
            "write",
            "writeString",
            "writeBytes",
            "writeAllBytes",
            "writeAllLines",
            "newOutputStream",
            "newBufferedWriter",
            "createFile",
            "createDirectory",
            "createDirectories",
            "createTempFile",
            "createTempDirectory",
            "copy",
            "move",
            "delete",
            "deleteIfExists"
    );

    @Override
    public String id() {
        return "file-state";
    }

    @Override
    public String description() {
        return "Detects local file I/O operations that may cause issues in replicated environments";
    }

    @Override
    public List<Finding> detect(CallGraph graph, LeafTypeConfig config, Set<String> reachableClasses) {
        List<Finding> findings = new ArrayList<>();

        for (ClassNode cls : graph.allClasses()) {
            // Only analyze classes reachable from project roots
            if (!reachableClasses.contains(cls.fqn())) {
                continue;
            }

            // Check for file I/O type fields
            for (FieldNode field : cls.fields()) {
                String typeName = field.extractTypeName();
                if (typeName == null) continue;

                if (config.isFileStateType(typeName)) {
                    findings.add(createFieldFinding(cls, field, typeName));
                }
            }

            // Check for Files.write* method invocations
            for (MethodNode method : cls.methods()) {
                for (MethodRef invocation : method.invocations()) {
                    if (isFileWriteInvocation(invocation)) {
                        findings.add(createMethodFinding(cls, method, invocation));
                    }
                }
            }
        }

        return findings;
    }

    private boolean isFileWriteInvocation(MethodRef invocation) {
        String owner = invocation.owner();
        String methodName = invocation.name();

        // Check for java.nio.file.Files write methods
        if ("java.nio.file.Files".equals(owner)) {
            return FILES_WRITE_METHODS.contains(methodName);
        }

        return false;
    }

    private Finding createFieldFinding(ClassNode cls, FieldNode field, String typeName) {
        FileStateCategory category = categorizeField(typeName);

        return Finding.builder()
                .className(cls.fqn())
                .fieldName(field.name())
                .fieldType(field.type())
                .stateType(StateType.IN_MEMORY) // File state is local, like in-memory
                .riskLevel(category.riskLevel())
                .pattern(category.pattern())
                .description(buildFieldDescription(cls, field, category))
                .recommendation(category.recommendation())
                .detectorId(id())
                .sourceFile(cls.sourceFile())
                .build();
    }

    private Finding createMethodFinding(ClassNode cls, MethodNode method, MethodRef invocation) {
        return Finding.builder()
                .className(cls.fqn())
                .fieldName(method.name() + "() -> " + invocation.name() + "()")
                .fieldType("java.nio.file.Files")
                .stateType(StateType.IN_MEMORY)
                .riskLevel(RiskLevel.HIGH)
                .pattern("File system write operation")
                .description(String.format(
                        "Method '%s' in class '%s' calls Files.%s() which writes to local file system. " +
                        "This data is NOT shared across replicas.",
                        method.name(),
                        cls.simpleName(),
                        invocation.name()
                ))
                .recommendation("Use distributed storage (S3, GCS, Azure Blob) or a database instead of local files")
                .detectorId(id())
                .sourceFile(cls.sourceFile())
                .build();
    }

    private FileStateCategory categorizeField(String typeName) {
        if (typeName.contains("RandomAccessFile")) {
            return FileStateCategory.RANDOM_ACCESS_FILE;
        }
        if (typeName.contains("FileOutputStream")) {
            return FileStateCategory.FILE_OUTPUT_STREAM;
        }
        if (typeName.contains("FileWriter")) {
            return FileStateCategory.FILE_WRITER;
        }
        if (typeName.contains("FileChannel")) {
            return FileStateCategory.FILE_CHANNEL;
        }
        return FileStateCategory.OTHER;
    }

    private String buildFieldDescription(ClassNode cls, FieldNode field, FileStateCategory category) {
        return String.format(
                "Class '%s' has file I/O field '%s' of type %s. %s",
                cls.simpleName(),
                field.name(),
                category.name(),
                category.note()
        );
    }

    private enum FileStateCategory {
        RANDOM_ACCESS_FILE(
                RiskLevel.CRITICAL,
                "RandomAccessFile - direct file mutation",
                "Replace with distributed storage (database, S3, Redis) for horizontal scaling",
                "RandomAccessFile allows direct file modification which is NOT replicated."
        ),
        FILE_OUTPUT_STREAM(
                RiskLevel.HIGH,
                "FileOutputStream - local file write",
                "Replace with distributed storage or cloud object storage",
                "Output streams write to local disk which is NOT shared across replicas."
        ),
        FILE_WRITER(
                RiskLevel.HIGH,
                "FileWriter - local text file write",
                "Replace with distributed storage or database for shared access",
                "FileWriter creates local files invisible to other replicas."
        ),
        FILE_CHANNEL(
                RiskLevel.HIGH,
                "FileChannel - low-level file I/O",
                "Replace with distributed storage for horizontal scaling",
                "FileChannel operates on local file system only."
        ),
        OTHER(
                RiskLevel.MEDIUM,
                "File I/O operation",
                "Consider using distributed storage for horizontal scaling",
                "Local file operations are not replicated across instances."
        );

        private final RiskLevel riskLevel;
        private final String pattern;
        private final String recommendation;
        private final String note;

        FileStateCategory(RiskLevel riskLevel, String pattern, String recommendation, String note) {
            this.riskLevel = riskLevel;
            this.pattern = pattern;
            this.recommendation = recommendation;
            this.note = note;
        }

        public RiskLevel riskLevel() { return riskLevel; }
        public String pattern() { return pattern; }
        public String recommendation() { return recommendation; }
        public String note() { return note; }
    }
}
