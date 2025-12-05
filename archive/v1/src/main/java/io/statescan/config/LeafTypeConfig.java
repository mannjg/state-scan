package io.statescan.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Configuration for leaf types - types that indicate stateful components.
 * Loaded from YAML configuration files.
 */
public class LeafTypeConfig {

    private static final String DEFAULT_CONFIG = "/leaf-types.yaml";

    private final Set<String> singletonAnnotations;
    private final Set<String> sessionAnnotations;
    private final Set<String> requestAnnotations;
    private final Set<String> externalStateTypes;
    private final Set<String> serviceClientTypes;
    private final Set<String> grpcTypes;
    private final Set<String> resilienceTypes;
    private final Set<String> cacheTypes;
    private final Set<String> endpointAnnotations;
    private final Set<String> mutableCollectionTypes;
    private final Set<String> moduleTypes;
    private final Set<String> threadLocalTypes;
    private final Set<String> fileStateTypes;
    private final Set<String> safeTypes;
    private final List<Pattern> excludePatterns;

    private LeafTypeConfig(Map<String, Object> config) {
        this.singletonAnnotations = getStringSet(config, "singletonAnnotations");
        this.sessionAnnotations = getStringSet(config, "sessionAnnotations");
        this.requestAnnotations = getStringSet(config, "requestAnnotations");
        this.externalStateTypes = getStringSet(config, "externalStateTypes");
        this.serviceClientTypes = getStringSet(config, "serviceClientTypes");
        this.grpcTypes = getStringSet(config, "grpcTypes");
        this.resilienceTypes = getStringSet(config, "resilienceTypes");
        this.cacheTypes = getStringSet(config, "cacheTypes");
        this.endpointAnnotations = getStringSet(config, "endpointAnnotations");
        this.mutableCollectionTypes = getStringSet(config, "mutableCollectionTypes");
        this.moduleTypes = getStringSet(config, "moduleTypes");
        this.threadLocalTypes = getStringSet(config, "threadLocalTypes");
        this.fileStateTypes = getStringSet(config, "fileStateTypes");
        this.safeTypes = getStringSet(config, "safeTypes");
        this.excludePatterns = compilePatterns(getStringSet(config, "excludePatterns"));
    }

    @SuppressWarnings("unchecked")
    private Set<String> getStringSet(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof List<?> list) {
            Set<String> result = new HashSet<>();
            for (Object item : list) {
                if (item instanceof String s) {
                    result.add(s);
                }
            }
            return Set.copyOf(result);
        }
        return Set.of();
    }

    /**
     * Compiles a set of regex pattern strings into Pattern objects.
     * Invalid patterns are logged and skipped.
     */
    private List<Pattern> compilePatterns(Set<String> patterns) {
        List<Pattern> compiled = new ArrayList<>();
        for (String regex : patterns) {
            try {
                compiled.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                System.err.println("Warning: Invalid exclude pattern '" + regex + "': " + e.getMessage());
            }
        }
        return List.copyOf(compiled);
    }

    /**
     * Loads the default configuration from the classpath.
     */
    public static LeafTypeConfig loadDefault() {
        try (InputStream is = LeafTypeConfig.class.getResourceAsStream(DEFAULT_CONFIG)) {
            if (is == null) {
                throw new RuntimeException("Default configuration not found: " + DEFAULT_CONFIG);
            }
            return load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default configuration", e);
        }
    }

    /**
     * Loads configuration from a file path.
     */
    public static LeafTypeConfig loadFromFile(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return load(is);
        }
    }

    /**
     * Loads configuration from an input stream.
     */
    @SuppressWarnings("unchecked")
    public static LeafTypeConfig load(InputStream is) {
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(is);
        return new LeafTypeConfig(config != null ? config : Map.of());
    }

    /**
     * Merges this configuration with another, with the other taking precedence.
     */
    public LeafTypeConfig merge(LeafTypeConfig other) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("singletonAnnotations", mergeSet(this.singletonAnnotations, other.singletonAnnotations));
        merged.put("sessionAnnotations", mergeSet(this.sessionAnnotations, other.sessionAnnotations));
        merged.put("requestAnnotations", mergeSet(this.requestAnnotations, other.requestAnnotations));
        merged.put("externalStateTypes", mergeSet(this.externalStateTypes, other.externalStateTypes));
        merged.put("serviceClientTypes", mergeSet(this.serviceClientTypes, other.serviceClientTypes));
        merged.put("grpcTypes", mergeSet(this.grpcTypes, other.grpcTypes));
        merged.put("resilienceTypes", mergeSet(this.resilienceTypes, other.resilienceTypes));
        merged.put("cacheTypes", mergeSet(this.cacheTypes, other.cacheTypes));
        merged.put("endpointAnnotations", mergeSet(this.endpointAnnotations, other.endpointAnnotations));
        merged.put("mutableCollectionTypes", mergeSet(this.mutableCollectionTypes, other.mutableCollectionTypes));
        merged.put("moduleTypes", mergeSet(this.moduleTypes, other.moduleTypes));
        merged.put("threadLocalTypes", mergeSet(this.threadLocalTypes, other.threadLocalTypes));
        merged.put("fileStateTypes", mergeSet(this.fileStateTypes, other.fileStateTypes));
        merged.put("safeTypes", mergeSet(this.safeTypes, other.safeTypes));
        merged.put("excludePatterns", mergePatterns(this.excludePatterns, other.excludePatterns));
        return new LeafTypeConfig(merged);
    }

    private List<String> mergeSet(Set<String> a, Set<String> b) {
        Set<String> merged = new HashSet<>(a);
        merged.addAll(b);
        return new ArrayList<>(merged);
    }

    private List<String> mergePatterns(List<Pattern> a, List<Pattern> b) {
        Set<String> merged = new HashSet<>();
        for (Pattern p : a) {
            merged.add(p.pattern());
        }
        for (Pattern p : b) {
            merged.add(p.pattern());
        }
        return new ArrayList<>(merged);
    }

    // ---- Query methods ----

    public boolean isSingletonAnnotation(String annotation) {
        return singletonAnnotations.contains(annotation);
    }

    public boolean isSessionAnnotation(String annotation) {
        return sessionAnnotations.contains(annotation);
    }

    public boolean isRequestAnnotation(String annotation) {
        return requestAnnotations.contains(annotation);
    }

    public boolean isExternalStateType(String type) {
        return matchesAny(type, externalStateTypes);
    }

    public boolean isServiceClientType(String type) {
        return matchesAny(type, serviceClientTypes);
    }

    public boolean isGrpcType(String type) {
        return matchesAny(type, grpcTypes);
    }

    public boolean isResilienceType(String type) {
        return matchesAny(type, resilienceTypes);
    }

    public boolean isCacheType(String type) {
        return matchesAny(type, cacheTypes);
    }

    public boolean isEndpointAnnotation(String annotation) {
        return endpointAnnotations.contains(annotation);
    }

    public boolean isMutableCollectionType(String type) {
        return matchesAny(type, mutableCollectionTypes);
    }

    public boolean isModuleType(String type) {
        return matchesAny(type, moduleTypes);
    }

    public boolean isThreadLocalType(String type) {
        return matchesAny(type, threadLocalTypes);
    }

    public boolean isFileStateType(String type) {
        return matchesAny(type, fileStateTypes);
    }

    public boolean isSafeType(String type) {
        return matchesAny(type, safeTypes);
    }

    /**
     * Checks if a type should be excluded from findings based on configured regex patterns.
     * Matches against the full type name (e.g., "io.prometheus.client.Counter").
     *
     * @param type the fully qualified type name to check
     * @return true if the type matches any exclude pattern
     */
    public boolean shouldExcludeType(String type) {
        if (type == null) return false;
        for (Pattern pattern : excludePatterns) {
            if (pattern.matcher(type).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the type matches any in the set (including subtype matching).
     */
    private boolean matchesAny(String type, Set<String> types) {
        if (type == null) return false;
        // Direct match
        if (types.contains(type)) return true;
        // Check if any configured type is a prefix (for subtype matching)
        for (String configured : types) {
            if (type.startsWith(configured)) return true;
        }
        return false;
    }

    // ---- Getters for direct access ----

    public Set<String> getSingletonAnnotations() {
        return singletonAnnotations;
    }

    public Set<String> getSessionAnnotations() {
        return sessionAnnotations;
    }

    public Set<String> getExternalStateTypes() {
        return externalStateTypes;
    }

    public Set<String> getServiceClientTypes() {
        return serviceClientTypes;
    }

    public Set<String> getGrpcTypes() {
        return grpcTypes;
    }

    public Set<String> getResilienceTypes() {
        return resilienceTypes;
    }

    public Set<String> getCacheTypes() {
        return cacheTypes;
    }

    public Set<String> getEndpointAnnotations() {
        return endpointAnnotations;
    }

    public Set<String> getMutableCollectionTypes() {
        return mutableCollectionTypes;
    }

    public Set<String> getModuleTypes() {
        return moduleTypes;
    }

    public Set<String> getThreadLocalTypes() {
        return threadLocalTypes;
    }

    public Set<String> getFileStateTypes() {
        return fileStateTypes;
    }

    public Set<String> getSafeTypes() {
        return safeTypes;
    }

    /**
     * Returns the compiled exclude patterns.
     */
    public List<Pattern> getExcludePatterns() {
        return excludePatterns;
    }

    /**
     * Returns true if any exclude patterns are configured.
     */
    public boolean hasExcludePatterns() {
        return !excludePatterns.isEmpty();
    }
}
