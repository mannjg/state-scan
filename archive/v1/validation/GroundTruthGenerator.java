import org.objectweb.asm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Bytecode-based ground truth generator for state-scan validation.
 *
 * Walks all .class files in target/classes directories and extracts
 * static mutable fields using ASM bytecode analysis.
 *
 * Usage:
 *   javac -cp "path/to/asm.jar:path/to/jackson.jar" GroundTruthGenerator.java
 *   java -cp ".:path/to/asm.jar:path/to/jackson.jar" GroundTruthGenerator /path/to/project
 */
public class GroundTruthGenerator {

    // Mutable collection type patterns (matches FieldNode.isPotentiallyMutable + config)
    private static final Set<String> MUTABLE_TYPE_PATTERNS = Set.of(
        // Core collections (from FieldNode.isPotentiallyMutable)
        "Map", "HashMap", "LinkedHashMap", "TreeMap", "ConcurrentHashMap",
        "List", "ArrayList", "LinkedList", "CopyOnWriteArrayList",
        "Set", "HashSet", "LinkedHashSet", "TreeSet", "CopyOnWriteArraySet",
        "Queue", "Deque", "LinkedBlockingQueue", "ArrayBlockingQueue",
        "ConcurrentLinkedQueue", "ConcurrentLinkedDeque", "Collection",

        // Atomic types (from FieldNode.isPotentiallyMutable)
        "Atomic", "AtomicReference", "AtomicInteger", "AtomicLong", "AtomicBoolean",

        // StringBuilder/StringBuffer (from FieldNode.isPotentiallyMutable)
        "StringBuilder", "StringBuffer",

        // Cache types (from leaf-types.yaml cacheTypes)
        "Cache", "LoadingCache", "AsyncCache", "Ehcache",

        // Metrics types (from leaf-types.yaml mutableCollectionTypes)
        "Counter", "Gauge", "Histogram", "Summary", "Timer", "DistributionSummary",
        "Recorder", "ConcurrentHistogram",

        // ThreadLocal types (from leaf-types.yaml threadLocalTypes)
        "ThreadLocal", "FastThreadLocal", "InheritableThreadLocal"
    );

    // Safe types to exclude (from leaf-types.yaml safeTypes)
    private static final Set<String> SAFE_TYPE_PATTERNS = Set.of(
        // Loggers
        "Logger", "Log",

        // Thread-safe utilities
        "Pattern",
        "ObjectMapper", "ObjectReader", "ObjectWriter",
        "DateTimeFormatter",

        // Reflection - method handles are immutable references (from leaf-types.yaml)
        "Method",  // java.lang.reflect.Method

        // Lock-free primitives (not mutable state)
        "AtomicIntegerFieldUpdater", "AtomicLongFieldUpdater", "AtomicReferenceFieldUpdater",

        // Protobuf internals
        "MethodDescriptor", "ServiceDescriptor", "FileDescriptor",
        "EnumLiteMap", "Descriptors", "FieldAccessorTable", "Parser",

        // Immutable collections
        "ImmutableMap", "ImmutableList", "ImmutableSet",
        "UnmodifiableMap", "UnmodifiableList", "UnmodifiableSet"
    );

    private final List<GroundTruthEntry> findings = new ArrayList<>();
    private final Path projectRoot;
    private final String packagePrefix;

    public GroundTruthGenerator(Path projectRoot, String packagePrefix) {
        this.projectRoot = projectRoot;
        this.packagePrefix = packagePrefix;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java GroundTruthGenerator <project-path> [package-prefix]");
            System.err.println("Example: java GroundTruthGenerator /home/user/pulsar org.apache.pulsar");
            System.exit(1);
        }

        Path projectPath = Path.of(args[0]);
        String packagePrefix = args.length > 1 ? args[1] : "";

        GroundTruthGenerator generator = new GroundTruthGenerator(projectPath, packagePrefix);
        generator.generate();
        generator.writeOutput();
    }

    public void generate() throws IOException {
        // Find all target/classes directories
        List<Path> classesDirs;
        try (Stream<Path> stream = Files.walk(projectRoot, 10)) {
            classesDirs = stream
                .filter(p -> p.endsWith("target/classes"))
                .filter(Files::isDirectory)
                .filter(this::hasClassFiles)
                .toList();
        }

        System.err.println("Found " + classesDirs.size() + " target/classes directories:");
        for (Path p : classesDirs) {
            System.err.println("  " + p);
        }

        int totalClasses = 0;
        for (Path classesDir : classesDirs) {
            int count = processDirectory(classesDir);
            totalClasses += count;
        }

        System.err.println("Processed " + totalClasses + " classes");
        System.err.println("Found " + findings.size() + " static mutable fields");
    }

    private boolean hasClassFiles(Path dir) {
        try (Stream<Path> stream = Files.walk(dir, 5)) {
            return stream.anyMatch(p -> p.toString().endsWith(".class"));
        } catch (IOException e) {
            return false;
        }
    }

    private int processDirectory(Path classesDir) throws IOException {
        int count = 0;
        try (Stream<Path> stream = Files.walk(classesDir)) {
            List<Path> classFiles = stream
                .filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.toString().contains("$"))  // Skip inner classes for now
                .toList();

            for (Path classFile : classFiles) {
                processClassFile(classFile);
                count++;
            }
        }
        return count;
    }

    private void processClassFile(Path classFile) {
        try {
            byte[] bytes = Files.readAllBytes(classFile);
            ClassReader reader = new ClassReader(bytes);

            StaticFieldVisitor visitor = new StaticFieldVisitor();
            reader.accept(visitor, 0);

            // Filter by package prefix if specified
            if (!packagePrefix.isEmpty() && !visitor.className.startsWith(packagePrefix.replace('.', '/'))) {
                return;
            }

            for (FieldInfo field : visitor.staticFields) {
                if (shouldInclude(field)) {
                    String pattern = categorizeField(field);
                    String riskLevel = determineRisk(field);

                    findings.add(new GroundTruthEntry(
                        visitor.className.replace('/', '.'),
                        field.name,
                        field.descriptor,
                        field.isFinal,
                        pattern,
                        riskLevel,
                        visitor.sourceFile
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing " + classFile + ": " + e.getMessage());
        }
    }

    private boolean shouldInclude(FieldInfo field) {
        String typeName = extractTypeName(field.descriptor);

        // Exclude safe types first
        for (String safe : SAFE_TYPE_PATTERNS) {
            if (typeName.contains(safe)) {
                return false;
            }
        }

        // Non-final static fields are mutable (same as FieldNode.isPotentiallyMutable)
        if (!field.isFinal) {
            // Skip primitive types
            if (isPrimitive(field.descriptor)) {
                return false;
            }
            // Skip String constants
            if (field.descriptor.equals("Ljava/lang/String;")) {
                return false;
            }
            // Skip Class literals
            if (field.descriptor.equals("Ljava/lang/Class;")) {
                return false;
            }
            // All other non-final static object fields are mutable
            return true;
        }

        // Final static fields - check if mutable type (same as FieldNode.isPotentiallyMutable)
        // Collection types
        if (typeName.contains("Map") || typeName.contains("List") ||
            typeName.contains("Set") || typeName.contains("Collection") ||
            typeName.contains("Queue") || typeName.contains("Deque")) {
            return true;
        }

        // Atomic types
        if (typeName.contains("Atomic")) {
            return true;
        }

        // StringBuilder/StringBuffer
        if (typeName.contains("StringBuilder") || typeName.contains("StringBuffer")) {
            return true;
        }

        // ThreadLocal types - must END with ThreadLocal (not just contain it)
        // to avoid matching classes like "ThreadLocalStateCleaner"
        if (typeName.endsWith("ThreadLocal") || typeName.endsWith("FastThreadLocal")) {
            return true;
        }

        // Cache types
        if (typeName.contains("Cache") || typeName.contains("LoadingCache") ||
            typeName.contains("AsyncCache") || typeName.contains("Ehcache")) {
            return true;
        }

        // Metrics types
        if (typeName.contains("Counter") || typeName.contains("Gauge") ||
            typeName.contains("Histogram") || typeName.contains("Summary") ||
            typeName.contains("Recorder") || typeName.contains("Timer") ||
            typeName.contains("DistributionSummary") || typeName.contains("ConcurrentHistogram")) {
            return true;
        }

        return false;
    }

    private boolean isPrimitive(String descriptor) {
        return descriptor.length() == 1 && "ZBCSIJFD".indexOf(descriptor.charAt(0)) >= 0;
    }

    private String extractTypeName(String descriptor) {
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            String fullName = descriptor.substring(1, descriptor.length() - 1);
            int lastSlash = fullName.lastIndexOf('/');
            return lastSlash >= 0 ? fullName.substring(lastSlash + 1) : fullName;
        }
        if (descriptor.startsWith("[")) {
            return "Array";
        }
        return descriptor;
    }

    private String categorizeField(FieldInfo field) {
        String typeName = extractTypeName(field.descriptor);

        if (!field.isFinal) {
            return "static_non_final";
        }

        // Check if type IS a ThreadLocal (not just contains "ThreadLocal" in name)
        // Must end with ThreadLocal or FastThreadLocal to be an actual ThreadLocal type
        if (typeName.endsWith("ThreadLocal") || typeName.endsWith("FastThreadLocal") ||
            typeName.equals("java.lang.ThreadLocal") ||
            typeName.equals("java.lang.InheritableThreadLocal") ||
            typeName.equals("io.netty.util.concurrent.FastThreadLocal")) {
            return "thread_local";
        }
        if (typeName.contains("Cache") || typeName.contains("LoadingCache")) {
            return "cache";
        }
        if (typeName.contains("Counter") || typeName.contains("Gauge") ||
            typeName.contains("Histogram") || typeName.contains("Summary") ||
            typeName.contains("Recorder") || typeName.contains("Timer")) {
            return "metrics";
        }
        if (typeName.contains("Atomic")) {
            return "atomic";
        }
        if (typeName.contains("Map") || typeName.contains("List") ||
            typeName.contains("Set") || typeName.contains("Queue")) {
            return "static_mutable_collection";
        }

        return "static_mutable_other";
    }

    private String determineRisk(FieldInfo field) {
        String typeName = extractTypeName(field.descriptor);

        if (!field.isFinal) {
            return "CRITICAL";
        }
        if (typeName.contains("Cache")) {
            return "CRITICAL";
        }
        if (typeName.contains("Map") || typeName.contains("List") || typeName.contains("Set")) {
            return "HIGH";
        }
        // Check if type IS a ThreadLocal (not just contains "ThreadLocal" in name)
        if (typeName.endsWith("ThreadLocal") || typeName.endsWith("FastThreadLocal") ||
            typeName.equals("java.lang.ThreadLocal") ||
            typeName.equals("java.lang.InheritableThreadLocal") ||
            typeName.equals("io.netty.util.concurrent.FastThreadLocal")) {
            return "HIGH";
        }
        if (typeName.contains("Counter") || typeName.contains("Gauge")) {
            return "MEDIUM";  // Metrics are intentional
        }

        return "MEDIUM";
    }

    public void writeOutput() throws IOException {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("generator", "GroundTruthGenerator");
        output.put("version", "1.0");
        output.put("projectRoot", projectRoot.toString());
        output.put("packagePrefix", packagePrefix);
        output.put("totalFindings", findings.size());
        output.put("groundTruth", findings);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Write to stdout for piping, stderr for status
        mapper.writeValue(System.out, output);
    }

    // Inner classes

    static class StaticFieldVisitor extends ClassVisitor {
        String className;
        String sourceFile;
        boolean isEnum;
        final List<FieldInfo> staticFields = new ArrayList<>();

        StaticFieldVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                         String superName, String[] interfaces) {
            this.className = name;
            this.isEnum = (access & Opcodes.ACC_ENUM) != 0;
        }

        @Override
        public void visitSource(String source, String debug) {
            this.sourceFile = source;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            boolean isFinal = (access & Opcodes.ACC_FINAL) != 0;
            boolean isSynthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;

            if (isStatic && !isSynthetic) {
                // Skip enum $VALUES
                if ("$VALUES".equals(name)) {
                    return null;
                }

                // Skip enum constants (type matches declaring class in enum)
                if (isEnum && descriptor.equals("L" + className + ";")) {
                    return null;
                }

                // Skip enum constant arrays
                if (isEnum && descriptor.equals("[L" + className + ";")) {
                    return null;
                }

                staticFields.add(new FieldInfo(name, descriptor, isFinal, signature));
            }
            return null;
        }
    }

    static class FieldInfo {
        final String name;
        final String descriptor;
        final boolean isFinal;
        final String signature;

        FieldInfo(String name, String descriptor, boolean isFinal, String signature) {
            this.name = name;
            this.descriptor = descriptor;
            this.isFinal = isFinal;
            this.signature = signature;
        }
    }

    static class GroundTruthEntry {
        public final String className;
        public final String fieldName;
        public final String fieldType;
        public final boolean isFinal;
        public final String pattern;
        public final String riskLevel;
        public final String sourceFile;

        GroundTruthEntry(String className, String fieldName, String fieldType,
                        boolean isFinal, String pattern, String riskLevel, String sourceFile) {
            this.className = className;
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.isFinal = isFinal;
            this.pattern = pattern;
            this.riskLevel = riskLevel;
            this.sourceFile = sourceFile;
        }
    }
}
