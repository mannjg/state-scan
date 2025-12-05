package io.statescan;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration loaded from a YAML file.
 * Contains project-specific settings for scanning.
 */
public class ScanConfig {

    private final Path projectPath;
    private final Set<String> packages;
    private final Set<String> excludePackages;
    private final Set<String> rootPackages;
    private final List<String> excludeClasses;
    private final List<String> excludeMethods;

    private ScanConfig(Path projectPath,
                       Set<String> packages,
                       Set<String> excludePackages,
                       Set<String> rootPackages,
                       List<String> excludeClasses,
                       List<String> excludeMethods) {
        this.projectPath = projectPath;
        this.packages = packages;
        this.excludePackages = excludePackages;
        this.rootPackages = rootPackages;
        this.excludeClasses = excludeClasses;
        this.excludeMethods = excludeMethods;
    }

    /**
     * Load configuration from a YAML file.
     */
    @SuppressWarnings("unchecked")
    public static ScanConfig load(Path configPath) throws IOException {
        Yaml yaml = new Yaml();

        try (InputStream in = Files.newInputStream(configPath)) {
            Map<String, Object> data = yaml.load(in);
            if (data == null) {
                throw new IOException("Empty or invalid config file: " + configPath);
            }

            // Required: projectPath
            String projectPathStr = (String) data.get("projectPath");
            if (projectPathStr == null || projectPathStr.isBlank()) {
                throw new IOException("Config file must specify 'projectPath'");
            }
            Path projectPath = Path.of(projectPathStr);

            // Optional: packages (default: empty = scan all)
            Set<String> packages = toSet((List<String>) data.get("packages"));

            // Optional: excludePackages
            Set<String> excludePackages = toSet((List<String>) data.get("excludePackages"));

            // Optional: rootPackages
            Set<String> rootPackages = toSet((List<String>) data.get("rootPackages"));

            // Optional: excludeClasses (for callgraph pruning)
            List<String> excludeClasses = toList((List<String>) data.get("excludeClasses"));

            // Optional: excludeMethods (for callgraph pruning at method level)
            List<String> excludeMethods = toList((List<String>) data.get("excludeMethods"));

            return new ScanConfig(projectPath, packages, excludePackages, rootPackages, excludeClasses, excludeMethods);
        }
    }

    private static Set<String> toSet(List<String> list) {
        if (list == null || list.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : list) {
            if (item != null) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static List<String> toList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
            .filter(s -> s != null && !s.trim().isEmpty())
            .map(String::trim)
            .toList();
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public Set<String> getPackages() {
        return packages;
    }

    public Set<String> getExcludePackages() {
        return excludePackages;
    }

    public Set<String> getRootPackages() {
        return rootPackages;
    }

    public List<String> getExcludeClasses() {
        return excludeClasses;
    }

    public List<String> getExcludeMethods() {
        return excludeMethods;
    }

    /**
     * Check if a value matches a pattern.
     * Trailing dot means prefix match, otherwise exact match.
     */
    private static boolean matchesPattern(String value, String pattern) {
        if (pattern.isEmpty()) {
            return true; // Empty pattern matches everything
        }
        if (pattern.endsWith(".")) {
            // Prefix match
            return value.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        // Exact match
        return value.equals(pattern);
    }

    /**
     * Check if a class FQN is excluded from callgraph traversal.
     * Checks both excludePackages (prefix match) and excludeClasses (pattern match).
     * For excludeClasses: trailing dot means prefix match, otherwise exact match.
     */
    public boolean isClassExcludedFromCallgraph(String fqn) {
        // Check excludePackages first (always prefix match)
        for (String pkg : excludePackages) {
            if (fqn.startsWith(pkg)) {
                return true;
            }
        }
        
        // Then check excludeClasses patterns
        for (String pattern : excludeClasses) {
            if (matchesPattern(fqn, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a method is excluded from callgraph traversal.
     * Pattern formats:
     * - "methodName" - excludes method in any class
     * - "methodName." - prefix match on method name (any class)
     * - "ClassName#methodName" - excludes specific method on class
     * - "ClassName#methodName." - prefix match on method name
     * - "package.prefix.#methodName" - excludes method on any class in package
     * - "#methodName" - same as "methodName" (empty class = any)
     * - "ClassName#" - excludes all methods on class
     */
    public boolean isMethodExcludedFromCallgraph(String classFqn, String methodName) {
        for (String pattern : excludeMethods) {
            int hashIdx = pattern.indexOf('#');
            if (hashIdx == -1) {
                // Unqualified: just method pattern, applies to any class
                if (matchesPattern(methodName, pattern)) {
                    return true;
                }
            } else {
                // Qualified: class#method
                String classPattern = pattern.substring(0, hashIdx);
                String methodPattern = pattern.substring(hashIdx + 1);
                
                // Empty classPattern means "any class"
                boolean classMatches = classPattern.isEmpty() || matchesPattern(classFqn, classPattern);
                // Empty methodPattern means "any method"
                boolean methodMatches = methodPattern.isEmpty() || matchesPattern(methodName, methodPattern);
                
                if (classMatches && methodMatches) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if rootPackages are configured (enables dependency scanning).
     */
    public boolean hasRootPackages() {
        return !rootPackages.isEmpty();
    }
}
