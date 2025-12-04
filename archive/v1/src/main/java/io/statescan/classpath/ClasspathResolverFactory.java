package io.statescan.classpath;

import java.nio.file.Path;
import java.util.List;

/**
 * Factory for creating the appropriate ClasspathResolver based on project type.
 */
public class ClasspathResolverFactory {

    private static final List<ClasspathResolver> RESOLVERS = List.of(
            new MavenClasspathResolver()
            // Future: new GradleClasspathResolver()
    );

    /**
     * Returns the appropriate resolver for the given project path.
     *
     * @param projectPath Path to the project root
     * @return The appropriate ClasspathResolver
     * @throws IllegalArgumentException if no resolver supports the project
     */
    public static ClasspathResolver forProject(Path projectPath) {
        for (ClasspathResolver resolver : RESOLVERS) {
            if (resolver.supports(projectPath)) {
                return resolver;
            }
        }
        throw new IllegalArgumentException(
                "No supported build system found at: " + projectPath +
                        ". Supported: Maven (pom.xml)");
    }

    /**
     * Checks if any resolver supports the given project.
     */
    public static boolean isSupported(Path projectPath) {
        return RESOLVERS.stream().anyMatch(r -> r.supports(projectPath));
    }

    /**
     * Returns all available resolvers.
     */
    public static List<ClasspathResolver> availableResolvers() {
        return RESOLVERS;
    }
}
