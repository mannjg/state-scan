package io.statescan.report;

import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.ScanReport;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Formats scan results as a standalone HTML report.
 */
public class HtmlReporter implements Reporter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String format() {
        return "html";
    }

    @Override
    public void write(ScanReport report, Writer writer) throws IOException {
        PrintWriter out = new PrintWriter(writer);

        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("  <meta charset=\"UTF-8\">");
        out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        out.println("  <title>State-Scan Report</title>");
        writeStyles(out);
        out.println("</head>");
        out.println("<body>");

        writeHeader(out, report);
        writeSummary(out, report);
        writeFindings(out, report);
        writeFooter(out);

        out.println("</body>");
        out.println("</html>");
        out.flush();
    }

    private void writeStyles(PrintWriter out) {
        out.println("  <style>");
        out.println("    :root {");
        out.println("      --critical: #dc3545;");
        out.println("      --high: #fd7e14;");
        out.println("      --medium: #ffc107;");
        out.println("      --low: #28a745;");
        out.println("      --info: #17a2b8;");
        out.println("    }");
        out.println("    * { box-sizing: border-box; margin: 0; padding: 0; }");
        out.println("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
        out.println("           line-height: 1.6; color: #333; background: #f5f5f5; }");
        out.println("    .container { max-width: 1200px; margin: 0 auto; padding: 20px; }");
        out.println("    header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); ");
        out.println("             color: white; padding: 40px 20px; text-align: center; }");
        out.println("    header h1 { font-size: 2.5em; margin-bottom: 10px; }");
        out.println("    header p { opacity: 0.9; }");
        out.println("    .summary { display: flex; gap: 20px; flex-wrap: wrap; margin: 20px 0; }");
        out.println("    .summary-card { background: white; border-radius: 8px; padding: 20px; ");
        out.println("                    flex: 1; min-width: 150px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        out.println("    .summary-card h3 { font-size: 2em; }");
        out.println("    .summary-card.critical { border-left: 4px solid var(--critical); }");
        out.println("    .summary-card.critical h3 { color: var(--critical); }");
        out.println("    .summary-card.high { border-left: 4px solid var(--high); }");
        out.println("    .summary-card.high h3 { color: var(--high); }");
        out.println("    .summary-card.medium { border-left: 4px solid var(--medium); }");
        out.println("    .summary-card.low { border-left: 4px solid var(--low); }");
        out.println("    .summary-card.info { border-left: 4px solid var(--info); }");
        out.println("    .findings-section { margin: 30px 0; }");
        out.println("    .findings-section h2 { margin-bottom: 15px; padding-bottom: 10px; border-bottom: 2px solid #ddd; }");
        out.println("    .finding { background: white; border-radius: 8px; padding: 20px; margin-bottom: 15px; ");
        out.println("               box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        out.println("    .finding.critical { border-left: 4px solid var(--critical); }");
        out.println("    .finding.high { border-left: 4px solid var(--high); }");
        out.println("    .finding.medium { border-left: 4px solid var(--medium); }");
        out.println("    .finding.low { border-left: 4px solid var(--low); }");
        out.println("    .finding.info { border-left: 4px solid var(--info); }");
        out.println("    .finding h3 { display: flex; justify-content: space-between; align-items: center; }");
        out.println("    .finding .badge { font-size: 0.75em; padding: 4px 8px; border-radius: 4px; color: white; }");
        out.println("    .badge.critical { background: var(--critical); }");
        out.println("    .badge.high { background: var(--high); }");
        out.println("    .badge.medium { background: var(--medium); color: #333; }");
        out.println("    .badge.low { background: var(--low); }");
        out.println("    .badge.info { background: var(--info); }");
        out.println("    .finding .location { color: #666; font-size: 0.9em; margin: 5px 0; }");
        out.println("    .finding .field { font-family: monospace; background: #f0f0f0; padding: 8px; ");
        out.println("                      border-radius: 4px; margin: 10px 0; }");
        out.println("    .finding .description { margin: 10px 0; }");
        out.println("    .finding .recommendation { background: #e8f5e9; padding: 10px; border-radius: 4px; ");
        out.println("                               margin-top: 10px; border-left: 3px solid var(--low); }");
        out.println("    footer { text-align: center; padding: 20px; color: #666; font-size: 0.9em; }");
        out.println("    .meta { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); ");
        out.println("            gap: 10px; margin: 20px 0; background: white; padding: 20px; border-radius: 8px; }");
        out.println("    .meta-item { }");
        out.println("    .meta-item label { color: #666; font-size: 0.85em; }");
        out.println("  </style>");
    }

    private void writeHeader(PrintWriter out, ScanReport report) {
        out.println("<header>");
        out.println("  <h1>State-Scan Report</h1>");
        out.println("  <p>Stateful Component Analysis</p>");
        out.println("</header>");
        out.println("<div class=\"container\">");

        out.println("<div class=\"meta\">");
        out.println("  <div class=\"meta-item\"><label>Project</label><div>" + escape(report.projectPath().toString()) + "</div></div>");
        out.println("  <div class=\"meta-item\"><label>Scan Date</label><div>" + report.scanDate().format(DATE_FORMAT) + "</div></div>");
        out.println("  <div class=\"meta-item\"><label>Classes Scanned</label><div>" + report.classesScanned() + "</div></div>");
        out.println("  <div class=\"meta-item\"><label>JARs Scanned</label><div>" + report.jarsScanned() + "</div></div>");
        out.println("</div>");
    }

    private void writeSummary(PrintWriter out, ScanReport report) {
        Map<RiskLevel, List<Finding>> byRisk = report.findingsByRiskLevel();

        int critical = byRisk.getOrDefault(RiskLevel.CRITICAL, List.of()).size();
        int high = byRisk.getOrDefault(RiskLevel.HIGH, List.of()).size();
        int medium = byRisk.getOrDefault(RiskLevel.MEDIUM, List.of()).size();
        int low = byRisk.getOrDefault(RiskLevel.LOW, List.of()).size();
        int info = byRisk.getOrDefault(RiskLevel.INFO, List.of()).size();

        out.println("<div class=\"summary\">");
        out.println("  <div class=\"summary-card critical\"><h3>" + critical + "</h3><div>Critical</div></div>");
        out.println("  <div class=\"summary-card high\"><h3>" + high + "</h3><div>High</div></div>");
        out.println("  <div class=\"summary-card medium\"><h3>" + medium + "</h3><div>Medium</div></div>");
        out.println("  <div class=\"summary-card low\"><h3>" + low + "</h3><div>Low</div></div>");
        out.println("  <div class=\"summary-card info\"><h3>" + info + "</h3><div>Info</div></div>");
        out.println("</div>");
    }

    private void writeFindings(PrintWriter out, ScanReport report) {
        Map<RiskLevel, List<Finding>> byRisk = report.findingsByRiskLevel();

        for (RiskLevel level : RiskLevel.values()) {
            List<Finding> findings = byRisk.getOrDefault(level, List.of());
            if (!findings.isEmpty()) {
                writeFindingSection(out, level, findings);
            }
        }
    }

    private void writeFindingSection(PrintWriter out, RiskLevel level, List<Finding> findings) {
        String levelName = level.name().charAt(0) + level.name().substring(1).toLowerCase();

        out.println("<div class=\"findings-section\">");
        out.println("  <h2>" + levelName + " Findings (" + findings.size() + ")</h2>");

        for (Finding finding : findings) {
            String levelClass = level.name().toLowerCase();

            out.println("  <div class=\"finding " + levelClass + "\">");
            out.println("    <h3>");
            out.println("      <span>" + escape(finding.simpleClassName()) + "</span>");
            out.println("      <span class=\"badge " + levelClass + "\">" + level.name() + "</span>");
            out.println("    </h3>");
            out.println("    <div class=\"location\">" + escape(finding.location()) + "</div>");

            if (finding.fieldName() != null) {
                out.println("    <div class=\"field\">" + escape(finding.fieldSignature().orElse(finding.fieldName())) + "</div>");
            }

            out.println("    <div><strong>Pattern:</strong> " + escape(finding.pattern()) + "</div>");

            if (finding.description() != null) {
                out.println("    <div class=\"description\">" + escape(finding.description()) + "</div>");
            }

            if (finding.recommendation() != null) {
                out.println("    <div class=\"recommendation\"><strong>Recommendation:</strong> " +
                        escape(finding.recommendation()) + "</div>");
            }

            if (finding.scopeSource() != Finding.ScopeSource.NONE) {
                out.println("    <div><small>Scope: " + finding.scopeSource().displayName());
                if (finding.scopeAnnotation() != null) {
                    out.println(" (" + escape(finding.scopeAnnotation()) + ")");
                }
                out.println("</small></div>");
            }

            out.println("  </div>");
        }

        out.println("</div>");
    }

    private void writeFooter(PrintWriter out) {
        out.println("</div>"); // container
        out.println("<footer>");
        out.println("  <p>Generated by State-Scan | Stateful Component Detection Tool</p>");
        out.println("</footer>");
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
