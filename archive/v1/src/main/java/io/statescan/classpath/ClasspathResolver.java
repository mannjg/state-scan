package io.statescan.classpath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Resolves the classpath for a project, including all transitive dependencies.
 */
public interface ClasspathResolver {

    /**
     * Resolves the complete classpath for a project.
     *
     * @param projectPath Path to the project root directory
     * @return Resolved classpath information
     * @throws IOException If the project cannot be read
     * @throws ClasspathResolutionException If dependencies cannot be resolved
     */
    ResolvedClasspath resolve(Path projectPath) throws IOException, ClasspathResolutionException;

    /**
     * Checks if this resolver can handle the given project.
     *
     * @param projectPath Path to the project root
     * @return true if this resolver supports the project type
     */
    boolean supports(Path projectPath);

    /**
     * Returns a description of this resolver (e.g., "Maven", "Gradle").
     */
    String name();

    /**
     * Resolved classpath containing project classes and dependencies.
     * Supports multi-module Maven projects with multiple class directories.
     */
    record ResolvedClasspath(
            Path projectClassesDir,
            List<Path> projectClassesDirs,  // For multi-module projects
            List<Path> dependencyJars,
            String detectedPackagePrefix
    ) {
        /**
         * Constructor for single-module projects.
         */
        public ResolvedClasspath(Path projectClassesDir, List<Path> dependencyJars, String detectedPackagePrefix) {
            this(projectClassesDir,
                 projectClassesDir != null ? List.of(projectClassesDir) : List.of(),
                 dependencyJars,
                 detectedPackagePrefix);
        }

        /**
         * Returns all project class directories (supports multi-module).
         */
        public List<Path> allProjectClassesDirs() {
            return projectClassesDirs != null ? projectClassesDirs :
                   (projectClassesDir != null ? List.of(projectClassesDir) : List.of());
        }

        /**
         * Returns all paths (classes dirs + all JARs).
         */
        public List<Path> allPaths() {
            var all = new java.util.ArrayList<Path>();
            all.addAll(allProjectClassesDirs());
            all.addAll(dependencyJars);
            return all;
        }
    }

    /**
     * Exception thrown when classpath resolution fails.
     */
    class ClasspathResolutionException extends Exception {
        public ClasspathResolutionException(String message) {
            super(message);
        }

        public ClasspathResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
