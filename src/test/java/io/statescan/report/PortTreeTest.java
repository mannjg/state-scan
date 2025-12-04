package io.statescan.report;

import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.StateType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortTreeTest {

    @Test
    void buildTree_groupsByCategory() {
        List<Finding> findings = List.of(
                createPathFinding("Path to externalStateTypes",
                        "javax.sql.DataSource",
                        List.of("MyService", "DataRepo", "DataSource")),
                createPathFinding("Path to cacheTypes",
                        "com.github.benmanes.caffeine.cache.Cache",
                        List.of("CacheService", "Cache"))
        );

        List<PortTree.CategoryNode> tree = PortTree.buildTree(findings);

        assertThat(tree).hasSize(2);
        assertThat(tree).extracting(PortTree.CategoryNode::categoryId)
                .containsExactlyInAnyOrder("externalStateTypes", "cacheTypes");
    }

    @Test
    void buildTree_groupsByLeafTypeWithinCategory() {
        List<Finding> findings = List.of(
                createPathFinding("Path to externalStateTypes",
                        "javax.sql.DataSource",
                        List.of("ServiceA", "DataSource")),
                createPathFinding("Path to externalStateTypes",
                        "javax.sql.DataSource",
                        List.of("ServiceB", "DataSource")),
                createPathFinding("Path to externalStateTypes",
                        "org.rocksdb.RocksDB",
                        List.of("MetadataStore", "RocksDB"))
        );

        List<PortTree.CategoryNode> tree = PortTree.buildTree(findings);

        assertThat(tree).hasSize(1);
        PortTree.CategoryNode dbCategory = tree.get(0);
        assertThat(dbCategory.displayName()).isEqualTo("DATABASE");
        assertThat(dbCategory.totalPaths()).isEqualTo(3);
        assertThat(dbCategory.leafTypes()).hasSize(2);
    }

    @Test
    void buildTree_formatsPathsWithSimpleNames() {
        List<Finding> findings = List.of(
                createPathFinding("Path to serviceClientTypes",
                        "java.net.http.HttpClient",
                        List.of("com.example.MyService", "com.example.HttpWrapper", "java.net.http.HttpClient"))
        );

        List<PortTree.CategoryNode> tree = PortTree.buildTree(findings);

        assertThat(tree).hasSize(1);
        PortTree.LeafTypeNode leafType = tree.get(0).leafTypes().get(0);
        assertThat(leafType.simpleLeafType()).isEqualTo("HttpClient");

        PortTree.PathEntry path = leafType.paths().get(0);
        assertThat(path.pathString()).isEqualTo("MyService -> HttpWrapper -> HttpClient");
    }

    @Test
    void buildTree_sortsCategoriesByPathCountDescending() {
        List<Finding> findings = List.of(
                createPathFinding("Path to cacheTypes", "Cache1", List.of("A", "Cache1")),
                createPathFinding("Path to externalStateTypes", "DB1", List.of("B", "DB1")),
                createPathFinding("Path to externalStateTypes", "DB2", List.of("C", "DB2")),
                createPathFinding("Path to externalStateTypes", "DB3", List.of("D", "DB3"))
        );

        List<PortTree.CategoryNode> tree = PortTree.buildTree(findings);

        assertThat(tree).hasSize(2);
        // externalStateTypes has 3 paths, cacheTypes has 1
        assertThat(tree.get(0).categoryId()).isEqualTo("externalStateTypes");
        assertThat(tree.get(1).categoryId()).isEqualTo("cacheTypes");
    }

    @Test
    void buildTree_filtersOutNonPathFindings() {
        List<Finding> findings = List.of(
                createPathFinding("Path to externalStateTypes", "DataSource", List.of("A", "B")),
                Finding.builder()
                        .className("SomeClass")
                        .pattern("Static mutable field")  // Not a path finding
                        .stateType(StateType.IN_MEMORY)
                        .riskLevel(RiskLevel.HIGH)
                        .build()
        );

        List<PortTree.CategoryNode> tree = PortTree.buildTree(findings);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).totalPaths()).isEqualTo(1);
    }

    @Test
    void buildTree_returnsEmptyForNoPathFindings() {
        List<Finding> findings = List.of(
                Finding.builder()
                        .className("SomeClass")
                        .pattern("Static mutable field")
                        .stateType(StateType.IN_MEMORY)
                        .riskLevel(RiskLevel.HIGH)
                        .build()
        );

        List<PortTree.CategoryNode> tree = PortTree.buildTree(findings);

        assertThat(tree).isEmpty();
    }

    private Finding createPathFinding(String pattern, String className, List<String> path) {
        return Finding.builder()
                .className(className)
                .pattern(pattern)
                .stateType(StateType.EXTERNAL_SERVICE)
                .riskLevel(RiskLevel.HIGH)
                .reachabilityPath(path)
                .rootClass(path.get(0))
                .build();
    }
}
