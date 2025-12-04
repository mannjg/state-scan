package io.statescan.report;

import io.statescan.graph.CallGraph;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.ScanReport;
import io.statescan.util.TypeUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formats scan results for console output with ANSI colors.
 * <p>
 * Provides an aggregated view by default:
 * - Summary header with compact stats
 * - Pattern breakdown showing counts
 * - External Ports tree (paths to external dependencies)
 * - Held State by class (grouped findings)
 * <p>
 * Use --detailed flag for the original flat listing.
 */
public class ConsoleReporter implements Reporter {

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";

    // Unicode tree-drawing characters
    private static final String TREE_BRANCH = "\u251C\u2500\u2500 ";  // ├──
    private static final String TREE_LAST = "\u2514\u2500\u2500 ";    // └──
    private static final String TREE_PIPE = "\u2502   ";              // │

    private final boolean useColors;
    private final boolean detailed;
    private final CallGraph graph;

    public ConsoleReporter() {
        this(true, false, null);
    }

    public ConsoleReporter(boolean useColors) {
        this(useColors, false, null);
    }

    public ConsoleReporter(boolean useColors, boolean detailed) {
        this(useColors, detailed, null);
    }

    public ConsoleReporter(boolean useColors, boolean detailed, CallGraph graph) {
        this.useColors = useColors;
        this.detailed = detailed;
        this.graph = graph;
    }

    @Override
    public String format() {
        return "console";
    }

    @Override
    public void write(ScanReport report, Writer writer) throws IOException {
        PrintWriter out = new PrintWriter(writer);

        printHeader(out, report);
        printCompactSummary(out, report);
        printPatternBreakdown(out, report);

        // External Ports section (Mode 2 findings with paths)
        List<Finding> pathFindings = report.pathFindings();
        if (!pathFindings.isEmpty()) {
            printExternalPorts(out, pathFindings);
        }

        // Held State section (Mode 1 findings)
        List<Finding> heldState = report.heldStateFindings();
        if (!heldState.isEmpty()) {
            printHeldStateByClass(out, heldState);
        }

        // Optional detailed listing (for --detailed flag)
        if (detailed) {
            printDetailedFindings(out, report);
        }

        printFooter(out, report);
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
        out.println();
    }

    private void printCompactSummary(PrintWriter out, ScanReport report) {
        out.println(bold("SUMMARY"));
        out.println(line('-', 70));

        // Compact one-line stats
        String stats = String.format("Scanned: %,d classes | %d JARs | %.1fs",
                report.classesScanned(),
                report.jarsScanned(),
                report.scanDurationMs() / 1000.0);
        out.println(stats);

        // Findings by severity on one line
        long critical = report.criticalCount();
        long high = report.highCount();
        long medium = report.mediumCount();
        long low = report.lowCount();

        StringBuilder findings = new StringBuilder("Findings: ");
        if (critical > 0) {
            findings.append(color(RED, critical + " critical")).append(" | ");
        } else {
            findings.append("0 critical | ");
        }
        if (high > 0) {
            findings.append(color(YELLOW, high + " high")).append(" | ");
        } else {
            findings.append("0 high | ");
        }
        findings.append(medium).append(" medium | ").append(low).append(" low");

        out.println(findings.toString());

        // Class count
        int uniqueClasses = report.uniqueClassCount();
        out.println("Affected: " + uniqueClasses + " classes");
        out.println();
    }

    private void printPatternBreakdown(PrintWriter out, ScanReport report) {
        Map<String, List<Finding>> byPattern = report.findingsByPattern();

        if (byPattern.isEmpty()) {
            return;
        }

        out.println(bold("PATTERN BREAKDOWN"));
        out.println(line('-', 40));

        // Sort by count descending and show top 10
        byPattern.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(10)
                .forEach(entry -> {
                    String pattern = entry.getKey();
                    List<Finding> findings = entry.getValue();
                    int fieldCount = findings.size();
                    int classCount = (int) findings.stream()
                            .map(Finding::className)
                            .distinct()
                            .count();
                    out.printf("  %s: %d fields (%d classes)%n", pattern, fieldCount, classCount);
                });

        int remaining = byPattern.size() - 10;
        if (remaining > 0) {
            out.println("  ... and " + remaining + " more patterns");
        }

        out.println();
    }

    private void printExternalPorts(PrintWriter out, List<Finding> pathFindings) {
        if (graph == null) {
            // Skip external ports display if no CallGraph available (shouldn't happen in normal use)
            return;
        }

        List<PortTree.CategoryNode> tree = PortTree.buildTree(pathFindings, graph);

        if (tree.isEmpty()) {
            return;
        }

        int totalPaths = pathFindings.size();
        out.println(bold("EXTERNAL PORTS") + color(CYAN, " (" + totalPaths + " paths)"));
        out.println(line('=', 70));

        for (int i = 0; i < tree.size(); i++) {
            PortTree.CategoryNode category = tree.get(i);

            out.println(bold(category.displayName()) +
                    color(CYAN, " (" + category.totalPaths() + " paths)"));

            List<PortTree.LeafTypeNode> leafTypes = category.leafTypes();
            for (int j = 0; j < leafTypes.size(); j++) {
                PortTree.LeafTypeNode leaf = leafTypes.get(j);
                boolean isLastLeaf = (j == leafTypes.size() - 1);
                String leafPrefix = isLastLeaf ? TREE_LAST : TREE_BRANCH;

                out.println(leafPrefix + leaf.simpleLeafType());

                // Show up to 3 example paths per leaf type
                List<PortTree.PathEntry> paths = leaf.paths();
                int pathLimit = Math.min(3, paths.size());
                for (int k = 0; k < pathLimit; k++) {
                    PortTree.PathEntry path = paths.get(k);
                    String pathPrefix = isLastLeaf ? "    " : TREE_PIPE;
                    String entryPrefix = (k == pathLimit - 1 && paths.size() <= 3) ? TREE_LAST : TREE_BRANCH;
                    out.println(pathPrefix + entryPrefix + path.pathString());
                }

                if (paths.size() > 3) {
                    String morePrefix = isLastLeaf ? "    " : TREE_PIPE;
                    out.println(morePrefix + TREE_LAST + "... and " + (paths.size() - 3) + " more paths");
                }
            }
            out.println();
        }
    }

    private void printHeldStateByClass(PrintWriter out, List<Finding> heldState) {
        List<ClassAggregation.ClassEntry> classes = ClassAggregation.aggregate(heldState);

        if (classes.isEmpty()) {
            return;
        }

        int totalClasses = classes.size();
        out.println(bold("HELD STATE BY CLASS") + color(CYAN, " (" + totalClasses + " classes)"));
        out.println(line('=', 70));

        for (ClassAggregation.ClassEntry classEntry : classes) {
            // Class header with finding count
            String riskIndicator = getRiskIndicator(classEntry.highestRisk());
            out.println(riskIndicator + " " + bold(classEntry.className()) +
                    color(CYAN, " [" + classEntry.findingCount() + " findings]"));

            // List fields
            for (ClassAggregation.FieldEntry field : classEntry.fields()) {
                String fieldType = field.fieldType() != null ? "(" + field.fieldType() + ")" : "";
                out.printf("  - %s %s - %s%n",
                        field.fieldName(),
                        fieldType,
                        field.pattern());
            }
            out.println();
        }
    }

    private void printDetailedFindings(PrintWriter out, ScanReport report) {
        out.println();
        out.println(bold("DETAILED FINDINGS"));
        out.println(line('=', 70));

        // Use existing grouped-by-risk logic for detailed mode
        Map<RiskLevel, List<Finding>> byRisk = report.findingsByRiskLevel();

        for (RiskLevel level : List.of(RiskLevel.CRITICAL, RiskLevel.HIGH,
                RiskLevel.MEDIUM, RiskLevel.LOW, RiskLevel.INFO)) {
            List<Finding> findings = byRisk.getOrDefault(level, List.of());
            if (!findings.isEmpty()) {
                printFindingGroup(out, level.name() + " FINDINGS", level, findings);
            }
        }
    }

    private void printFindingGroup(PrintWriter out, String title, RiskLevel level, List<Finding> findings) {
        String icon = getRiskIndicator(level);

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
            String cleanType = TypeUtils.cleanTypeName(finding.fieldType());
            String fieldSig = cleanType != null ?
                    cleanType + " " + finding.fieldName() :
                    finding.fieldName();
            out.println("    Field: " + fieldSig);
        }

        out.println("    Pattern: " + finding.pattern());

        if (finding.description() != null) {
            out.println("    Description: " + finding.description());
        }

        // Show reachability path if present
        if (finding.hasReachabilityPath()) {
            String cleanPath = finding.reachabilityPath().stream()
                    .map(TypeUtils::simpleClassName)
                    .collect(Collectors.joining(" -> "));
            out.println("    Path: " + cleanPath);
        }

        // Show recommendations only in detailed mode
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

    private void printFooter(PrintWriter out, ScanReport report) {
        out.println(line('=', 70));

        long critical = report.criticalCount();
        long high = report.highCount();

        if (critical > 0) {
            out.println(color(RED, bold("ACTION REQUIRED: " + critical +
                    " critical finding(s) must be addressed before horizontal scaling.")));
        } else if (high > 0) {
            out.println(color(YELLOW, "ATTENTION: " + high +
                    " high-risk finding(s) should be reviewed before horizontal scaling."));
        } else {
            out.println(color(GREEN, "No critical issues found. Review medium/low findings as needed."));
        }

        if (!detailed && report.totalFindings() > 0) {
            out.println();
            out.println("Run with --detailed for complete finding details.");
        }

        out.println();
    }

    private String getRiskIndicator(RiskLevel level) {
        return switch (level) {
            case CRITICAL -> color(RED, "[CRIT]");
            case HIGH -> color(YELLOW, "[HIGH]");
            case MEDIUM -> "[MED]";
            case LOW -> "[LOW]";
            case INFO -> color(CYAN, "[INFO]");
        };
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
