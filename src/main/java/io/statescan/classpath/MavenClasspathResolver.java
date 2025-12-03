package io.statescan.classpath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Resolves classpath for Maven projects by invoking Maven's dependency plugin.
 * This approach is more reliable than programmatic resolution as it uses
 * the project's actual Maven configuration and settings.
 */
public class MavenClasspathResolver implements ClasspathResolver {

    private static final String POM_FILE = "pom.xml";
    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("<groupId>([^<]+)</groupId>");
    private static final long MAVEN_TIMEOUT_SECONDS = 300; // 5 minutes

    @Override
    public ResolvedClasspath resolve(Path projectPath) throws IOException, ClasspathResolutionException {
        Path pomPath = projectPath.resolve(POM_FILE);
        if (!Files.exists(pomPath)) {
            throw new ClasspathResolutionException("No pom.xml found at: " + projectPath);
        }

        // Detect package prefix from groupId
        String packagePrefix = detectPackagePrefix(pomPath);

        // Check if this is a multi-module project
        List<Path> allClassesDirs = findAllClassesDirectories(projectPath);

        if (allClassesDirs.size() > 1) {
            // Multi-module project - use all target/classes directories
            System.out.println("Detected multi-module Maven project with " + allClassesDirs.size() + " modules");

            // For multi-module projects, try to resolve dependencies but fall back to local JARs if it fails
            List<Path> dependencyJars;
            try {
                dependencyJars = resolveDependencies(projectPath);
            } catch (ClasspathResolutionException e) {
                System.out.println("Maven dependency resolution failed, scanning local JARs from ~/.m2/repository");
                dependencyJars = findLocalMavenJars(projectPath, packagePrefix);
            }

            Path primaryClassesDir = allClassesDirs.isEmpty() ? null : allClassesDirs.get(0);
            return new ResolvedClasspath(primaryClassesDir, allClassesDirs, dependencyJars, packagePrefix);
        } else {
            // Single-module project - original behavior
            Path classesDir = allClassesDirs.isEmpty() ? null : allClassesDirs.get(0);
            List<Path> dependencyJars = resolveDependencies(projectPath);
            return new ResolvedClasspath(classesDir, dependencyJars, packagePrefix);
        }
    }

    @Override
    public boolean supports(Path projectPath) {
        return Files.exists(projectPath.resolve(POM_FILE));
    }

    @Override
    public String name() {
        return "Maven";
    }

    /**
     * Detects the package prefix from the pom.xml groupId.
     */
    private String detectPackagePrefix(Path pomPath) throws IOException {
        String content = Files.readString(pomPath);

        // Try to find the project's groupId (not parent's)
        // Look for groupId that's a direct child of project, not in parent block
        int parentStart = content.indexOf("<parent>");
        int parentEnd = content.indexOf("</parent>");

        String searchContent = content;
        if (parentStart >= 0 && parentEnd > parentStart) {
            // Remove parent block to avoid picking up parent's groupId
            searchContent = content.substring(0, parentStart) + content.substring(parentEnd + 9);
        }

        Matcher matcher = GROUP_ID_PATTERN.matcher(searchContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // Fall back to searching in full content (might get parent's groupId)
        matcher = GROUP_ID_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return "";
    }

    /**
     * Finds all compiled classes directories in the project.
     * For multi-module projects, walks the directory tree to find all target/classes directories.
     */
    private List<Path> findAllClassesDirectories(Path projectPath) throws IOException {
        List<Path> classesDirs = new ArrayList<>();

        // First check the standard location
        Path targetClasses = projectPath.resolve("target/classes");
        if (Files.exists(targetClasses) && Files.isDirectory(targetClasses)) {
            classesDirs.add(targetClasses);
        }

        // Walk the project to find all target/classes directories (multi-module support)
        try (Stream<Path> stream = Files.walk(projectPath, 10)) {
            stream.filter(p -> p.endsWith("target/classes"))
                  .filter(Files::isDirectory)
                  .filter(p -> !p.equals(targetClasses)) // Avoid duplicates
                  .filter(p -> hasClassFiles(p))         // Only include dirs with actual class files
                  .forEach(classesDirs::add);
        }

        return classesDirs;
    }

    /**
     * Checks if a directory contains any .class files (recursively).
     */
    private boolean hasClassFiles(Path dir) {
        try (Stream<Path> stream = Files.walk(dir, 5)) {
            return stream.anyMatch(p -> p.toString().endsWith(".class"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Finds JARs from the local Maven repository for the project's dependencies.
     * Used as a fallback when Maven dependency resolution fails.
     */
    private List<Path> findLocalMavenJars(Path projectPath, String packagePrefix) throws IOException {
        List<Path> jars = new ArrayList<>();

        // Find the local Maven repository
        Path m2Repo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        if (!Files.exists(m2Repo)) {
            return jars;
        }

        // Convert package prefix to path (e.g., "org.apache.pulsar" -> "org/apache/pulsar")
        String prefixPath = packagePrefix.replace('.', '/');
        Path prefixDir = m2Repo.resolve(prefixPath);

        // Find all JARs under the package prefix in the Maven repo
        if (Files.exists(prefixDir)) {
            try (Stream<Path> stream = Files.walk(prefixDir)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                      .filter(p -> !p.toString().contains("-sources"))
                      .filter(p -> !p.toString().contains("-javadoc"))
                      .filter(p -> !p.toString().contains("-tests"))
                      .forEach(jars::add);
            }
        }

        // Also add common dependencies from well-known groups
        addCommonDependencyJars(m2Repo, jars);

        return jars;
    }

    /**
     * Adds common dependency JARs from well-known Maven groups.
     */
    private void addCommonDependencyJars(Path m2Repo, List<Path> jars) throws IOException {
        // Add Guice and related DI JARs
        String[] commonGroups = {
            "com/google/inject",
            "javax/inject",
            "jakarta/inject",
            "com/google/guava",
            "org/slf4j",
            "io/netty"
        };

        for (String group : commonGroups) {
            Path groupDir = m2Repo.resolve(group);
            if (Files.exists(groupDir)) {
                try (Stream<Path> stream = Files.walk(groupDir, 5)) {
                    stream.filter(p -> p.toString().endsWith(".jar"))
                          .filter(p -> !p.toString().contains("-sources"))
                          .filter(p -> !p.toString().contains("-javadoc"))
                          .limit(20) // Limit to avoid too many JARs per group
                          .forEach(jars::add);
                }
            }
        }
    }

    /**
     * Resolves all dependency JARs using Maven's dependency:build-classpath goal.
     */
    private List<Path> resolveDependencies(Path projectPath) throws ClasspathResolutionException {
        try {
            // Create temp file for classpath output
            Path classpathFile = Files.createTempFile("state-scan-classpath", ".txt");

            try {
                // Run Maven to get classpath
                ProcessBuilder pb = new ProcessBuilder(
                        getMavenCommand(),
                        "dependency:build-classpath",
                        "-DincludeScope=runtime",
                        "-Dmdep.outputFile=" + classpathFile.toAbsolutePath(),
                        "-q" // Quiet mode
                );
                pb.directory(projectPath.toFile());
                pb.redirectErrorStream(true);

                Process process = pb.start();

                // Capture output for error reporting
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                boolean finished = process.waitFor(MAVEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new ClasspathResolutionException(
                            "Maven dependency resolution timed out after " + MAVEN_TIMEOUT_SECONDS + " seconds");
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    throw new ClasspathResolutionException(
                            "Maven dependency resolution failed with exit code " + exitCode + ":\n" + output);
                }

                // Parse the classpath file
                return parseClasspathFile(classpathFile);

            } finally {
                Files.deleteIfExists(classpathFile);
            }

        } catch (IOException | InterruptedException e) {
            throw new ClasspathResolutionException("Failed to resolve Maven dependencies", e);
        }
    }

    /**
     * Parses the classpath file generated by Maven.
     */
    private List<Path> parseClasspathFile(Path classpathFile) throws IOException {
        List<Path> jars = new ArrayList<>();

        if (!Files.exists(classpathFile)) {
            return jars;
        }

        String content = Files.readString(classpathFile).trim();
        if (content.isEmpty()) {
            return jars;
        }

        // Classpath entries are separated by the path separator (: on Unix, ; on Windows)
        String separator = System.getProperty("path.separator");
        String[] entries = content.split(Pattern.quote(separator));

        for (String entry : entries) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                Path jarPath = Path.of(trimmed);
                if (Files.exists(jarPath) && trimmed.endsWith(".jar")) {
                    jars.add(jarPath);
                }
            }
        }

        return jars;
    }

    /**
     * Gets the Maven command for the current platform.
     */
    private String getMavenCommand() {
        // Check for MAVEN_HOME
        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome != null) {
            Path mvnPath = Path.of(mavenHome, "bin", isWindows() ? "mvn.cmd" : "mvn");
            if (Files.exists(mvnPath)) {
                return mvnPath.toString();
            }
        }

        // Check for M2_HOME (older Maven installations)
        String m2Home = System.getenv("M2_HOME");
        if (m2Home != null) {
            Path mvnPath = Path.of(m2Home, "bin", isWindows() ? "mvn.cmd" : "mvn");
            if (Files.exists(mvnPath)) {
                return mvnPath.toString();
            }
        }

        // Fall back to PATH
        return isWindows() ? "mvn.cmd" : "mvn";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Alternative method: resolve dependencies programmatically using Maven Resolver API.
     * This is more complex but doesn't require Maven to be installed.
     * Currently not implemented - using Maven invocation instead.
     */
    @SuppressWarnings("unused")
    private List<Path> resolveDependenciesProgrammatic(Path projectPath) {
        // TODO: Implement using Maven Resolver API if needed
        // This would involve:
        // 1. Creating a RepositorySystem
        // 2. Creating a RepositorySystemSession
        // 3. Parsing pom.xml to extract dependencies
        // 4. Collecting and resolving dependencies
        // 5. Extracting artifact file paths
        throw new UnsupportedOperationException("Programmatic resolution not implemented");
    }
}
