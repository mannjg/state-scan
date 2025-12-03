package io.statescan.report;

import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.ScanReport;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * Formats scan results for console output with ANSI colors.
 */
public class ConsoleReporter implements Reporter {

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";

    private final boolean useColors;

    public ConsoleReporter() {
        this(true);
    }

    public ConsoleReporter(boolean useColors) {
        this.useColors = useColors;
    }

    @Override
    public String format() {
        return "console";
    }

    @Override
    public void write(ScanReport report, Writer writer) throws IOException {
        PrintWriter out = new PrintWriter(writer);

        printHeader(out, report);
        printSummary(out, report);

        // Print findings grouped by risk level
        Map<RiskLevel, List<Finding>> byRisk = report.findingsByRiskLevel();

        if (byRisk.containsKey(RiskLevel.CRITICAL) && !byRisk.get(RiskLevel.CRITICAL).isEmpty()) {
            printFindingGroup(out, "CRITICAL FINDINGS", RiskLevel.CRITICAL, byRisk.get(RiskLevel.CRITICAL));
        }

        if (byRisk.containsKey(RiskLevel.HIGH) && !byRisk.get(RiskLevel.HIGH).isEmpty()) {
            printFindingGroup(out, "HIGH RISK FINDINGS", RiskLevel.HIGH, byRisk.get(RiskLevel.HIGH));
        }

        if (byRisk.containsKey(RiskLevel.MEDIUM) && !byRisk.get(RiskLevel.MEDIUM).isEmpty()) {
            printFindingGroup(out, "MEDIUM RISK FINDINGS", RiskLevel.MEDIUM, byRisk.get(RiskLevel.MEDIUM));
        }

        if (byRisk.containsKey(RiskLevel.LOW) && !byRisk.get(RiskLevel.LOW).isEmpty()) {
            printFindingGroup(out, "LOW RISK FINDINGS", RiskLevel.LOW, byRisk.get(RiskLevel.LOW));
        }

        if (byRisk.containsKey(RiskLevel.INFO) && !byRisk.get(RiskLevel.INFO).isEmpty()) {
            printFindingGroup(out, "INFORMATIONAL", RiskLevel.INFO, byRisk.get(RiskLevel.INFO));
        }

        printRecommendations(out, report);
        out.flush();
    }

    private void printHeader(PrintWriter out, ScanReport report) {
        out.println();
        out.println(line('=', 70));
        out.println(center("STATE-SCAN REPORT", 70));
        out.println(line('=', 70));
        out.println();

        out.println("Project: " + report.projectPath().toString());
        out.println("Scan Date: " + report.scanDate());
        out.println("Classes Scanned: " + report.classesScanned());
        if (report.jarsScanned() > 0) {
            out.println("JARs Scanned: " + report.jarsScanned());
        }
        out.println();
    }

    private void printSummary(PrintWriter out, ScanReport report) {
        Map<RiskLevel, List<Finding>> byRisk = report.findingsByRiskLevel();

        int critical = byRisk.getOrDefault(RiskLevel.CRITICAL, List.of()).size();
        int high = byRisk.getOrDefault(RiskLevel.HIGH, List.of()).size();
        int medium = byRisk.getOrDefault(RiskLevel.MEDIUM, List.of()).size();
        int low = byRisk.getOrDefault(RiskLevel.LOW, List.of()).size();
        int info = byRisk.getOrDefault(RiskLevel.INFO, List.of()).size();

        out.println(bold("SUMMARY"));
        out.println(line('-', 70));

        if (critical > 0) {
            out.println(color(RED, "  CRITICAL: " + critical));
        } else {
            out.println("  Critical: 0");
        }

        if (high > 0) {
            out.println(color(YELLOW, "  HIGH: " + high));
        } else {
            out.println("  High: 0");
        }

        out.println("  Medium: " + medium);
        out.println("  Low: " + low);
        out.println("  Info: " + info);
        out.println();
    }

    private void printFindingGroup(PrintWriter out, String title, RiskLevel level, List<Finding> findings) {
        String icon = switch (level) {
            case CRITICAL -> color(RED, "[CRITICAL]");
            case HIGH -> color(YELLOW, "[HIGH]");
            case MEDIUM -> "[MEDIUM]";
            case LOW -> "[LOW]";
            case INFO -> color(CYAN, "[INFO]");
        };

        out.println(bold(icon + " " + title + " (" + findings.size() + ")"));
        out.println(line('-', 70));

        int index = 1;
        for (Finding finding : findings) {
            printFinding(out, index++, finding);
        }
        out.println();
    }

    private void printFinding(PrintWriter out, int index, Finding finding) {
        out.println("[" + index + "] " + bold(finding.simpleClassName()));
        out.println("    Location: " + finding.location());

        if (finding.fieldName() != null) {
            out.println("    Field: " + finding.fieldSignature().orElse(finding.fieldName()));
        }

        out.println("    Pattern: " + finding.pattern());

        if (finding.description() != null) {
            out.println("    Description: " + finding.description());
        }

        if (finding.recommendation() != null) {
            out.println("    " + color(GREEN, "Recommendation: " + finding.recommendation()));
        }

        if (finding.scopeSource() != Finding.ScopeSource.NONE) {
            out.println("    Scope Source: " + finding.scopeSource().displayName());
            if (finding.scopeAnnotation() != null) {
                out.println("    Annotation: " + finding.scopeAnnotation());
            }
            if (finding.scopeModule() != null) {
                out.println("    Module: " + finding.scopeModule());
            }
        }

        out.println();
    }

    private void printRecommendations(PrintWriter out, ScanReport report) {
        Map<RiskLevel, List<Finding>> byRisk = report.findingsByRiskLevel();
        int critical = byRisk.getOrDefault(RiskLevel.CRITICAL, List.of()).size();
        int high = byRisk.getOrDefault(RiskLevel.HIGH, List.of()).size();

        out.println(line('=', 70));

        if (critical > 0) {
            out.println(color(RED, bold("ACTION REQUIRED: " + critical +
                    " critical finding(s) must be addressed before horizontal scaling.")));
        } else if (high > 0) {
            out.println(color(YELLOW, "ATTENTION: " + high +
                    " high-risk finding(s) should be reviewed before horizontal scaling."));
        } else {
            out.println(color(GREEN, "No critical issues found. Review medium/low findings as needed."));
        }

        out.println();
    }

    // Formatting helpers

    private String color(String color, String text) {
        if (!useColors) return text;
        return color + text + RESET;
    }

    private String bold(String text) {
        if (!useColors) return text;
        return BOLD + text + RESET;
    }

    private String line(char c, int length) {
        return String.valueOf(c).repeat(length);
    }

    private String center(String text, int width) {
        if (text.length() >= width) return text;
        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text;
    }
}
