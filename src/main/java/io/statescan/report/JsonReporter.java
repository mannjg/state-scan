package io.statescan.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.ScanReport;
import io.statescan.model.StateType;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Formats scan results as JSON for machine processing.
 */
public class JsonReporter implements Reporter {

    private final ObjectMapper mapper;
    private final boolean prettyPrint;

    public JsonReporter() {
        this(true);
    }

    public JsonReporter(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
        this.mapper = createMapper();
    }

    private ObjectMapper createMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        if (prettyPrint) {
            m.enable(SerializationFeature.INDENT_OUTPUT);
        }
        return m;
    }

    @Override
    public String format() {
        return "json";
    }

    @Override
    public void write(ScanReport report, Writer writer) throws IOException {
        JsonReport jsonReport = toJsonReport(report);
        mapper.writeValue(writer, jsonReport);
    }

    private JsonReport toJsonReport(ScanReport report) {
        Map<RiskLevel, List<Finding>> byRisk = report.findingsByRiskLevel();

        return new JsonReport(
                new JsonReport.Metadata(
                        report.projectPath().toString(),
                        report.scanDate(),
                        report.classesScanned(),
                        report.jarsScanned(),
                        report.scanDurationMs()
                ),
                new JsonReport.Summary(
                        byRisk.getOrDefault(RiskLevel.CRITICAL, List.of()).size(),
                        byRisk.getOrDefault(RiskLevel.HIGH, List.of()).size(),
                        byRisk.getOrDefault(RiskLevel.MEDIUM, List.of()).size(),
                        byRisk.getOrDefault(RiskLevel.LOW, List.of()).size(),
                        byRisk.getOrDefault(RiskLevel.INFO, List.of()).size(),
                        report.totalFindings()
                ),
                report.findings().stream()
                        .map(this::toJsonFinding)
                        .toList()
        );
    }

    private JsonReport.Finding toJsonFinding(Finding finding) {
        return new JsonReport.Finding(
                finding.className(),
                finding.simpleClassName(),
                finding.lineNumber() > 0 ? finding.lineNumber() : null,
                finding.stateType().name(),
                finding.riskLevel().name(),
                finding.pattern(),
                finding.fieldName(),
                finding.fieldType(),
                finding.scopeSource() != Finding.ScopeSource.NONE ? finding.scopeSource().name() : null,
                finding.scopeAnnotation(),
                finding.scopeModule(),
                finding.description(),
                finding.recommendation(),
                finding.detectorId(),
                finding.sourceFile(),
                finding.affectedEndpoints().isEmpty() ? null : finding.affectedEndpoints()
        );
    }

    /**
     * JSON structure for the report.
     */
    public record JsonReport(
            Metadata metadata,
            Summary summary,
            List<Finding> findings
    ) {
        public record Metadata(
                String projectPath,
                LocalDateTime scanDate,
                int classesScanned,
                int jarsScanned,
                long scanDurationMs
        ) {}

        public record Summary(
                int critical,
                int high,
                int medium,
                int low,
                int info,
                int total
        ) {}

        public record Finding(
                String className,
                String simpleClassName,
                Integer lineNumber,
                String stateType,
                String riskLevel,
                String pattern,
                String fieldName,
                String fieldType,
                String scopeSource,
                String scopeAnnotation,
                String scopeModule,
                String description,
                String recommendation,
                String detectorId,
                String sourceFile,
                List<String> affectedEndpoints
        ) {}
    }
}
