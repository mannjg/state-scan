package io.statescan.model;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Complete scan report containing all findings and metadata.
 *
 * @param projectPath     Path to the scanned project
 * @param projectName     Name of the project (from pom.xml or directory name)
 * @param scanStartTime   When the scan started
 * @param scanDuration    How long the scan took
 * @param classesScanned  Number of classes analyzed
 * @param jarsScanned     Number of JAR files scanned
 * @param findings        All findings from the scan
 * @param configuration   Configuration used for the scan
 */
public record ScanReport(
        Path projectPath,
        String projectName,
        Instant scanStartTime,
        Duration scanDuration,
        int classesScanned,
        int jarsScanned,
        List<Finding> findings,
        ScanConfiguration configuration
) {
    /**
     * Configuration snapshot used for the scan.
     */
    public record ScanConfiguration(
            String projectPackagePrefix,
            RiskLevel minimumRiskLevel,
            List<String> excludePatterns
    ) {}

    /**
     * Compact constructor with validation.
     */
    public ScanReport {
        if (projectPath == null) {
            throw new IllegalArgumentException("projectPath cannot be null");
        }
        if (findings == null) {
            findings = List.of();
        } else {
            findings = List.copyOf(findings);
        }
    }

    /**
     * Returns findings filtered by minimum risk level.
     */
    public List<Finding> findingsAtLeast(RiskLevel minimumRisk) {
        return findings.stream()
                .filter(f -> f.riskLevel().isAtLeast(minimumRisk))
                .toList();
    }

    /**
     * Returns findings grouped by risk level.
     */
    public Map<RiskLevel, List<Finding>> findingsByRiskLevel() {
        return findings.stream()
                .collect(Collectors.groupingBy(Finding::riskLevel));
    }

    /**
     * Returns findings grouped by state type.
     */
    public Map<StateType, List<Finding>> findingsByStateType() {
        return findings.stream()
                .collect(Collectors.groupingBy(Finding::stateType));
    }

    /**
     * Returns count of findings by risk level.
     */
    public Map<RiskLevel, Long> findingCountsByRiskLevel() {
        return findings.stream()
                .collect(Collectors.groupingBy(Finding::riskLevel, Collectors.counting()));
    }

    /**
     * Returns true if there are any critical findings.
     */
    public boolean hasCriticalFindings() {
        return findings.stream()
                .anyMatch(f -> f.riskLevel() == RiskLevel.CRITICAL);
    }

    /**
     * Returns true if there are any findings at or above the given risk level.
     */
    public boolean hasFindingsAtLeast(RiskLevel minimumRisk) {
        return findings.stream()
                .anyMatch(f -> f.riskLevel().isAtLeast(minimumRisk));
    }

    /**
     * Returns the total number of findings.
     */
    public int totalFindings() {
        return findings.size();
    }

    /**
     * Returns the number of critical findings.
     */
    public long criticalCount() {
        return countByRiskLevel(RiskLevel.CRITICAL);
    }

    /**
     * Returns the number of high-risk findings.
     */
    public long highCount() {
        return countByRiskLevel(RiskLevel.HIGH);
    }

    /**
     * Returns the number of medium-risk findings.
     */
    public long mediumCount() {
        return countByRiskLevel(RiskLevel.MEDIUM);
    }

    /**
     * Returns the number of low-risk findings.
     */
    public long lowCount() {
        return countByRiskLevel(RiskLevel.LOW);
    }

    private long countByRiskLevel(RiskLevel level) {
        return findings.stream()
                .filter(f -> f.riskLevel() == level)
                .count();
    }

    /**
     * Returns the scan date as LocalDateTime.
     */
    public java.time.LocalDateTime scanDate() {
        return scanStartTime != null
                ? java.time.LocalDateTime.ofInstant(scanStartTime, java.time.ZoneId.systemDefault())
                : java.time.LocalDateTime.now();
    }

    /**
     * Returns the scan duration in milliseconds.
     */
    public long scanDurationMs() {
        return scanDuration != null ? scanDuration.toMillis() : 0;
    }

    /**
     * Builder for creating ScanReport instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path projectPath;
        private String projectName;
        private Instant scanStartTime;
        private Duration scanDuration;
        private int classesScanned;
        private int jarsScanned;
        private List<Finding> findings = List.of();
        private ScanConfiguration configuration;

        public Builder projectPath(Path projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder scanStartTime(Instant scanStartTime) {
            this.scanStartTime = scanStartTime;
            return this;
        }

        public Builder scanDuration(Duration scanDuration) {
            this.scanDuration = scanDuration;
            return this;
        }

        public Builder classesScanned(int classesScanned) {
            this.classesScanned = classesScanned;
            return this;
        }

        public Builder jarsScanned(int jarsScanned) {
            this.jarsScanned = jarsScanned;
            return this;
        }

        public Builder findings(List<Finding> findings) {
            this.findings = findings;
            return this;
        }

        public Builder configuration(ScanConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        public ScanReport build() {
            return new ScanReport(
                    projectPath,
                    projectName,
                    scanStartTime,
                    scanDuration,
                    classesScanned,
                    jarsScanned,
                    findings,
                    configuration
            );
        }
    }
}
