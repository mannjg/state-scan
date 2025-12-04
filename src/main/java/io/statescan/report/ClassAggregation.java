package io.statescan.report;

import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.util.TypeUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates findings by class for deduplicated display.
 * <p>
 * This is used to display the "HELD STATE BY CLASS" section, grouping
 * all findings for the same class together instead of listing them individually.
 */
public class ClassAggregation {

    /**
     * A class with its aggregated findings.
     *
     * @param className       Fully qualified class name
     * @param simpleClassName Simple class name for display
     * @param fields          List of fields with state issues
     * @param findingCount    Total number of findings for this class
     * @param highestRisk     The highest risk level among all findings
     */
    public record ClassEntry(
            String className,
            String simpleClassName,
            List<FieldEntry> fields,
            int findingCount,
            RiskLevel highestRisk
    ) {}

    /**
     * A field with a state issue.
     *
     * @param fieldName  Name of the field
     * @param fieldType  Clean type name of the field
     * @param pattern    Short pattern description
     * @param riskLevel  Risk level for this specific field
     */
    public record FieldEntry(
            String fieldName,
            String fieldType,
            String pattern,
            RiskLevel riskLevel
    ) {}

    /**
     * Groups findings by class and extracts field information.
     *
     * @param findings List of findings to aggregate
     * @return List of class entries sorted by risk level (highest first)
     */
    public static List<ClassEntry> aggregate(List<Finding> findings) {
        Map<String, List<Finding>> byClass = findings.stream()
                .collect(Collectors.groupingBy(Finding::className));

        List<ClassEntry> entries = new ArrayList<>();

        for (Map.Entry<String, List<Finding>> entry : byClass.entrySet()) {
            String className = entry.getKey();
            List<Finding> classFindings = entry.getValue();

            // Extract unique fields (dedup by field name)
            Map<String, FieldEntry> fieldMap = new LinkedHashMap<>();
            for (Finding f : classFindings) {
                if (f.fieldName() != null && !f.fieldName().isBlank()) {
                    String key = f.fieldName();
                    if (!fieldMap.containsKey(key)) {
                        fieldMap.put(key, new FieldEntry(
                                f.fieldName(),
                                TypeUtils.simpleTypeName(f.fieldType()),
                                extractShortPattern(f.pattern()),
                                f.riskLevel()
                        ));
                    }
                }
            }

            List<FieldEntry> fields = new ArrayList<>(fieldMap.values());

            // Find highest risk level
            RiskLevel highestRisk = classFindings.stream()
                    .map(Finding::riskLevel)
                    .min(Comparator.comparingInt(RiskLevel::severity))
                    .orElse(RiskLevel.INFO);

            entries.add(new ClassEntry(
                    className,
                    TypeUtils.simpleClassName(className),
                    fields,
                    classFindings.size(),
                    highestRisk
            ));
        }

        // Sort by risk level (highest/lowest severity number first), then by finding count
        entries.sort((a, b) -> {
            int riskCmp = Integer.compare(a.highestRisk().severity(), b.highestRisk().severity());
            if (riskCmp != 0) return riskCmp;
            return Integer.compare(b.findingCount(), a.findingCount());
        });

        return entries;
    }

    /**
     * Extracts a short pattern description for display.
     * Converts verbose patterns like "Static mutable field: HashMap with concurrent access"
     * to concise versions like "static mutable".
     */
    private static String extractShortPattern(String pattern) {
        if (pattern == null) return "";

        String lower = pattern.toLowerCase();

        // Match specific patterns first
        if (lower.contains("threadlocal")) return "ThreadLocal";
        if (lower.contains("singleton") && lower.contains("mutable")) return "singleton mutable";
        if (lower.contains("static") && lower.contains("non-final")) return "static non-final";
        if (lower.contains("static") && lower.contains("mutable")) return "static mutable";
        if (lower.contains("static") && lower.contains("final")) return "static final";
        if (lower.contains("cache")) return "cache";
        if (lower.contains("singleton")) return "singleton";
        if (lower.contains("file")) return "file I/O";
        if (lower.contains("resilience") || lower.contains("circuit")) return "resilience";

        // Default: truncate long patterns
        if (pattern.length() > 30) {
            return pattern.substring(0, 27) + "...";
        }

        return pattern;
    }
}
