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

    private ScanConfig(Path projectPath,
                       Set<String> packages,
                       Set<String> excludePackages,
                       Set<String> rootPackages,
                       List<String> excludeClasses) {
        this.projectPath = projectPath;
        this.packages = packages;
        this.excludePackages = excludePackages;
        this.rootPackages = rootPackages;
        this.excludeClasses = excludeClasses;
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

            return new ScanConfig(projectPath, packages, excludePackages, rootPackages, excludeClasses);
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

    /**
     * Check if a class FQN is excluded from callgraph traversal.
     * Trailing dot in pattern means prefix match, otherwise exact match.
     */
    public boolean isClassExcludedFromCallgraph(String fqn) {
        for (String pattern : excludeClasses) {
            if (pattern.endsWith(".")) {
                // Prefix match
                if (fqn.startsWith(pattern)) {
                    return true;
                }
            } else {
                // Exact match
                if (fqn.equals(pattern)) {
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
