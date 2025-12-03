package io.statescan.bytecode;

import io.statescan.graph.CallGraph;
import io.statescan.graph.ClassNode;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Scans bytecode from JARs and class directories to build the call graph.
 */
public class BytecodeScanner {

    private final String projectPackagePrefix;
    private final Set<String> excludePatterns;

    private int classesScanned = 0;
    private int jarsScanned = 0;

    public BytecodeScanner(String projectPackagePrefix) {
        this(projectPackagePrefix, Set.of());
    }

    public BytecodeScanner(String projectPackagePrefix, Set<String> excludePatterns) {
        this.projectPackagePrefix = projectPackagePrefix != null ? projectPackagePrefix : "";
        this.excludePatterns = excludePatterns != null ? excludePatterns : Set.of();
    }

    /**
     * Scans all classes from the given paths (directories and JARs).
     *
     * @param projectClassesPath Path to project's compiled classes (e.g., target/classes)
     * @param dependencyJars     List of dependency JAR files
     * @return CallGraph containing all scanned classes
     */
    public CallGraph scan(Path projectClassesPath, List<Path> dependencyJars) throws IOException {
        List<Path> classesDirs = projectClassesPath != null ? List.of(projectClassesPath) : List.of();
        return scanMultiple(classesDirs, dependencyJars);
    }

    /**
     * Scans all classes from multiple class directories and JARs.
     * Supports multi-module Maven projects.
     *
     * @param projectClassesDirs List of project class directories (e.g., multiple target/classes)
     * @param dependencyJars     List of dependency JAR files
     * @return CallGraph containing all scanned classes
     */
    public CallGraph scanMultiple(List<Path> projectClassesDirs, List<Path> dependencyJars) throws IOException {
        CallGraph.Builder graphBuilder = CallGraph.builder();

        // Scan all project class directories
        for (Path classesDir : projectClassesDirs) {
            if (classesDir != null && Files.exists(classesDir)) {
                scanDirectory(classesDir, true, graphBuilder);
            }
        }

        // Scan dependency JARs
        for (Path jarPath : dependencyJars) {
            if (Files.exists(jarPath) && jarPath.toString().endsWith(".jar")) {
                scanJar(jarPath, graphBuilder);
            }
        }

        // Build hierarchy and caller indexes
        graphBuilder.buildHierarchy();
        graphBuilder.buildCallerIndex();

        return graphBuilder.build();
    }

    /**
     * Scans a directory of class files recursively.
     */
    private void scanDirectory(Path directory, boolean isProjectClasses,
            CallGraph.Builder graphBuilder) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".class")) {
                    String relativePath = directory.relativize(file).toString();
                    String className = pathToClassName(relativePath);

                    if (!shouldExclude(className)) {
                        try (InputStream is = Files.newInputStream(file)) {
                            ClassNode classNode = scanClass(is, isProjectClasses);
                            if (classNode != null) {
                                graphBuilder.addClass(classNode);
                                classesScanned++;
                            }
                        } catch (Exception e) {
                            // Log but continue - some classes may fail to parse
                            System.err.println("Warning: Failed to scan class " + className + ": " + e.getMessage());
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Scans a JAR file for classes.
     */
    private void scanJar(Path jarPath, CallGraph.Builder graphBuilder) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            jarsScanned++;

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
                    String className = pathToClassName(name);

                    if (!shouldExclude(className)) {
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            // Determine if this is a project class based on package
                            boolean isProjectClass = isProjectPackage(className);
                            ClassNode classNode = scanClass(is, isProjectClass);
                            if (classNode != null) {
                                graphBuilder.addClass(classNode);
                                classesScanned++;
                            }
                        } catch (Exception e) {
                            // Log but continue
                            System.err.println("Warning: Failed to scan class " + className +
                                    " from " + jarPath.getFileName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * Scans a single class from an InputStream.
     */
    private ClassNode scanClass(InputStream is, boolean isProjectClass) throws IOException {
        ClassReader reader = new ClassReader(is);
        ClassVisitorAdapter visitor = new ClassVisitorAdapter();

        // Use SKIP_DEBUG to speed up scanning (we don't need debug info for most purposes)
        // Use SKIP_FRAMES to skip stack map frames (not needed for our analysis)
        reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return visitor.buildClassNode(isProjectClass);
    }

    /**
     * Converts a file path to a class name.
     * E.g., "com/company/MyClass.class" -> "com.company.MyClass"
     */
    private String pathToClassName(String path) {
        String withoutExtension = path;
        if (path.endsWith(".class")) {
            withoutExtension = path.substring(0, path.length() - 6);
        }
        return withoutExtension.replace('/', '.').replace('\\', '.');
    }

    /**
     * Checks if a class belongs to the project package.
     * Excludes shaded/vendored dependencies that are relocated into the project namespace.
     */
    private boolean isProjectPackage(String className) {
        if (projectPackagePrefix.isEmpty()) {
            return false; // Can't determine without a prefix
        }
        if (!className.startsWith(projectPackagePrefix)) {
            return false;
        }

        // Exclude shaded/vendored dependencies - these are third-party libraries
        // relocated into the project namespace to avoid classpath conflicts.
        // They should be treated as dependencies (not roots) for tree-shaking.
        if (className.contains(".shade.") ||
            className.contains(".shaded.") ||
            className.contains(".relocated.") ||
            className.contains(".repackaged.")) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a class should be excluded from scanning.
     */
    private boolean shouldExclude(String className) {
        for (String pattern : excludePatterns) {
            if (matchesPattern(className, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple glob-like pattern matching.
     * Supports * (any sequence) and ** (any sequence including .)
     */
    private boolean matchesPattern(String className, String pattern) {
        // Convert glob pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("**", "@@DOUBLESTAR@@")
                .replace("*", "[^.]*")
                .replace("@@DOUBLESTAR@@", ".*");
        return className.matches(regex);
    }

    /**
     * Returns the number of classes scanned.
     */
    public int getClassesScanned() {
        return classesScanned;
    }

    /**
     * Returns the number of JARs scanned.
     */
    public int getJarsScanned() {
        return jarsScanned;
    }

    /**
     * Convenience method to scan a single JAR file.
     */
    public static CallGraph scanSingleJar(Path jarPath, String projectPackagePrefix) throws IOException {
        BytecodeScanner scanner = new BytecodeScanner(projectPackagePrefix);
        return scanner.scan(null, List.of(jarPath));
    }

    /**
     * Convenience method to scan a project classes directory only.
     */
    public static CallGraph scanProjectClasses(Path classesDir, String projectPackagePrefix) throws IOException {
        BytecodeScanner scanner = new BytecodeScanner(projectPackagePrefix);
        return scanner.scan(classesDir, List.of());
    }
}
