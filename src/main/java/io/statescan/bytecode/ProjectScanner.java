package io.statescan.bytecode;

import io.statescan.model.ClassInfo;
import io.statescan.model.ScanResult;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans a project directory or JAR file to extract class information.
 * <p>
 * For Maven projects, looks for compiled classes in target/classes directories.
 * Can also scan JAR files directly.
 */
public class ProjectScanner {

    private final Set<String> packagePrefixes;
    private int classesScanned = 0;

    /**
     * Creates a ProjectScanner that filters classes by package prefix.
     *
     * @param packagePrefixes Set of package prefixes to include (empty means all)
     */
    public ProjectScanner(Set<String> packagePrefixes) {
        this.packagePrefixes = packagePrefixes != null ? packagePrefixes : Set.of();
    }

    /**
     * Creates a ProjectScanner that includes all classes.
     */
    public ProjectScanner() {
        this(Set.of());
    }

    /**
     * Scans a path (directory or JAR) and returns the scan result.
     *
     * @param path The path to scan
     * @return ScanResult containing all discovered classes
     * @throws IOException If an I/O error occurs
     */
    public ScanResult scan(Path path) throws IOException {
        Map<String, ClassInfo> classes = new LinkedHashMap<>();
        classesScanned = 0;

        if (Files.isDirectory(path)) {
            // Check if this is a Maven project
            Path targetClasses = path.resolve("target/classes");
            if (Files.isDirectory(targetClasses)) {
                scanDirectory(targetClasses, classes);
            } else {
                // Scan the directory directly (might be a classes directory already)
                scanDirectory(path, classes);
            }
        } else if (path.toString().endsWith(".jar")) {
            scanJar(path, classes);
        } else {
            throw new IllegalArgumentException("Path must be a directory or JAR file: " + path);
        }

        return new ScanResult(classes);
    }

    /**
     * Scans multiple paths and merges results.
     *
     * @param paths The paths to scan
     * @return Combined ScanResult
     * @throws IOException If an I/O error occurs
     */
    public ScanResult scanMultiple(List<Path> paths) throws IOException {
        Map<String, ClassInfo> allClasses = new LinkedHashMap<>();
        classesScanned = 0;

        for (Path path : paths) {
            ScanResult result = scan(path);
            allClasses.putAll(result.classes());
        }

        return new ScanResult(allClasses);
    }

    /**
     * Detects and scans a Maven multi-module project.
     *
     * @param projectRoot The root directory of the Maven project
     * @return ScanResult containing classes from all modules
     * @throws IOException If an I/O error occurs
     */
    public ScanResult scanMavenProject(Path projectRoot) throws IOException {
        Map<String, ClassInfo> classes = new LinkedHashMap<>();
        classesScanned = 0;

        // Check for root target/classes
        Path rootClasses = projectRoot.resolve("target/classes");
        if (Files.isDirectory(rootClasses)) {
            scanDirectory(rootClasses, classes);
        }

        // Look for module subdirectories with target/classes
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectRoot)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    Path moduleClasses = entry.resolve("target/classes");
                    if (Files.isDirectory(moduleClasses)) {
                        scanDirectory(moduleClasses, classes);
                    }
                }
            }
        }

        return new ScanResult(classes);
    }

    private void scanDirectory(Path directory, Map<String, ClassInfo> classes) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".class")) {
                    try (InputStream is = Files.newInputStream(file)) {
                        ClassInfo info = scanClass(is);
                        if (info != null && isProjectClass(info.fqn())) {
                            classes.put(info.fqn(), info);
                        }
                    } catch (Exception e) {
                        // Log and continue - some classes may fail to parse
                        System.err.println("Warning: Failed to scan " + file.getFileName() + ": " + e.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Skip files we can't read
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void scanJar(Path jarPath, Map<String, ClassInfo> classes) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // Skip non-class files and META-INF
                if (!name.endsWith(".class") || name.startsWith("META-INF/")) {
                    continue;
                }

                try (InputStream is = jarFile.getInputStream(entry)) {
                    ClassInfo info = scanClass(is);
                    if (info != null && isProjectClass(info.fqn())) {
                        classes.put(info.fqn(), info);
                    }
                } catch (Exception e) {
                    // Skip problematic classes
                }
            }
        }
    }

    private ClassInfo scanClass(InputStream is) throws IOException {
        classesScanned++;
        ClassReader reader = new ClassReader(is);
        ClassScanner scanner = new ClassScanner();
        // Use 0 flags to include debug info (for local variable names)
        reader.accept(scanner, 0);
        return scanner.buildClassInfo();
    }

    private boolean isProjectClass(String fqn) {
        if (packagePrefixes.isEmpty()) {
            return true;
        }
        return packagePrefixes.stream().anyMatch(fqn::startsWith);
    }

    /**
     * Returns the number of classes scanned in the last scan operation.
     */
    public int getClassesScanned() {
        return classesScanned;
    }
}
