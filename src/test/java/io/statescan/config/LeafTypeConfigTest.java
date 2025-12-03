package io.statescan.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LeafTypeConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadDefault_loadsSuccessfully() {
        LeafTypeConfig config = LeafTypeConfig.loadDefault();

        assertThat(config).isNotNull();
        assertThat(config.getSingletonAnnotations()).isNotEmpty();
        assertThat(config.getCacheTypes()).isNotEmpty();
    }

    @Test
    void isSingletonAnnotation_matchesConfiguredAnnotations() {
        LeafTypeConfig config = LeafTypeConfig.loadDefault();

        assertThat(config.isSingletonAnnotation("javax.inject.Singleton")).isTrue();
        assertThat(config.isSingletonAnnotation("jakarta.inject.Singleton")).isTrue();
        assertThat(config.isSingletonAnnotation("com.example.NotAnAnnotation")).isFalse();
    }

    @Test
    void isCacheType_matchesConfiguredTypes() {
        LeafTypeConfig config = LeafTypeConfig.loadDefault();

        assertThat(config.isCacheType("com.github.benmanes.caffeine.cache.Cache")).isTrue();
        assertThat(config.isCacheType("com.google.common.cache.Cache")).isTrue();
        assertThat(config.isCacheType("java.util.HashMap")).isFalse();
    }

    @Test
    void isExternalStateType_matchesConfiguredTypes() {
        LeafTypeConfig config = LeafTypeConfig.loadDefault();

        assertThat(config.isExternalStateType("javax.sql.DataSource")).isTrue();
        assertThat(config.isExternalStateType("java.sql.Connection")).isTrue();
        assertThat(config.isExternalStateType("java.lang.String")).isFalse();
    }

    @Test
    void loadFromFile_loadsCustomConfig() throws IOException {
        String yaml = """
            singletonAnnotations:
              - com.example.MySingleton
            cacheTypes:
              - com.example.MyCache
            """;
        Path configFile = tempDir.resolve("custom-config.yaml");
        Files.writeString(configFile, yaml);

        LeafTypeConfig config = LeafTypeConfig.loadFromFile(configFile);

        assertThat(config.isSingletonAnnotation("com.example.MySingleton")).isTrue();
        assertThat(config.isCacheType("com.example.MyCache")).isTrue();
    }

    @Test
    void merge_combinesBothConfigs() throws IOException {
        String yaml1 = """
            singletonAnnotations:
              - com.example.Anno1
            """;
        String yaml2 = """
            singletonAnnotations:
              - com.example.Anno2
            """;
        Path config1File = tempDir.resolve("config1.yaml");
        Path config2File = tempDir.resolve("config2.yaml");
        Files.writeString(config1File, yaml1);
        Files.writeString(config2File, yaml2);

        LeafTypeConfig config1 = LeafTypeConfig.loadFromFile(config1File);
        LeafTypeConfig config2 = LeafTypeConfig.loadFromFile(config2File);
        LeafTypeConfig merged = config1.merge(config2);

        assertThat(merged.isSingletonAnnotation("com.example.Anno1")).isTrue();
        assertThat(merged.isSingletonAnnotation("com.example.Anno2")).isTrue();
    }

    @Test
    void excludePatterns_matchesRegexPatterns() throws IOException {
        String yaml = """
            excludePatterns:
              - '.*\\.prometheus\\.client\\..*'
              - '.*Test$'
            """;
        Path configFile = tempDir.resolve("exclude-config.yaml");
        Files.writeString(configFile, yaml);

        LeafTypeConfig config = LeafTypeConfig.loadFromFile(configFile);

        assertThat(config.shouldExcludeType("io.prometheus.client.Counter")).isTrue();
        assertThat(config.shouldExcludeType("org.apache.pulsar.shade.io.prometheus.client.Gauge")).isTrue();
        assertThat(config.shouldExcludeType("com.example.MyServiceTest")).isTrue();
        assertThat(config.shouldExcludeType("java.util.HashMap")).isFalse();
        assertThat(config.shouldExcludeType("com.example.TestHelper")).isFalse(); // Test in middle, not end
    }

    @Test
    void excludePatterns_handlesNullType() throws IOException {
        String yaml = """
            excludePatterns:
              - '.*Test$'
            """;
        Path configFile = tempDir.resolve("exclude-config.yaml");
        Files.writeString(configFile, yaml);

        LeafTypeConfig config = LeafTypeConfig.loadFromFile(configFile);

        assertThat(config.shouldExcludeType(null)).isFalse();
    }

    @Test
    void excludePatterns_mergesCombinesPatterns() throws IOException {
        String yaml1 = """
            excludePatterns:
              - '.*\\.prometheus\\..*'
            """;
        String yaml2 = """
            excludePatterns:
              - '.*Test$'
            """;
        Path config1File = tempDir.resolve("config1.yaml");
        Path config2File = tempDir.resolve("config2.yaml");
        Files.writeString(config1File, yaml1);
        Files.writeString(config2File, yaml2);

        LeafTypeConfig config1 = LeafTypeConfig.loadFromFile(config1File);
        LeafTypeConfig config2 = LeafTypeConfig.loadFromFile(config2File);
        LeafTypeConfig merged = config1.merge(config2);

        assertThat(merged.shouldExcludeType("io.prometheus.client.Counter")).isTrue();
        assertThat(merged.shouldExcludeType("com.example.MyTest")).isTrue();
    }

    @Test
    void hasExcludePatterns_returnsTrueWhenPatternsExist() throws IOException {
        String yaml = """
            excludePatterns:
              - '.*Test$'
            """;
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, yaml);

        LeafTypeConfig config = LeafTypeConfig.loadFromFile(configFile);

        assertThat(config.hasExcludePatterns()).isTrue();
    }

    @Test
    void hasExcludePatterns_returnsFalseWhenNoPatterns() throws IOException {
        String yaml = """
            singletonAnnotations:
              - com.example.Anno
            """;
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, yaml);

        LeafTypeConfig config = LeafTypeConfig.loadFromFile(configFile);

        assertThat(config.hasExcludePatterns()).isFalse();
    }

    @Test
    void isSafeType_matchesConfiguredTypes() {
        LeafTypeConfig config = LeafTypeConfig.loadDefault();

        // Safe types should include loggers and similar
        assertThat(config.isSafeType("org.slf4j.Logger")).isTrue();
    }
}
