package io.statescan;

import io.statescan.bytecode.BytecodeScanner;
import io.statescan.classpath.ClasspathResolver;
import io.statescan.classpath.ClasspathResolver.ResolvedClasspath;
import io.statescan.classpath.ClasspathResolverFactory;
import io.statescan.config.LeafTypeConfig;
import io.statescan.detectors.DetectorRegistry;
import io.statescan.graph.CallGraph;
import io.statescan.graph.ReachabilityAnalyzer;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.ScanReport;
import io.statescan.report.ConsoleReporter;
import io.statescan.report.HtmlReporter;
import io.statescan.report.JsonReporter;
import io.statescan.report.Reporter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * CLI entry point for the state-scan tool.
 */
@Command(
        name = "state-scan",
        mixinStandardHelpOptions = true,
        version = "state-scan 1.0.0",
        description = "Scans Java projects for stateful components that may cause issues when horizontally scaling.",
        footer = {
                "",
                "Examples:",
                "  state-scan /path/to/project",
                "  state-scan /path/to/project --output-format json --output-file report.json",
                "  state-scan /path/to/project --package-prefix com.company --risk-threshold medium"
        }
)
public class StateScanCli implements Callable<Integer> {

    @Parameters(
            index = "0",
            description = "Path to the Java project to scan (directory containing pom.xml or build.gradle)"
    )
    private Path projectPath;

    @Option(
            names = {"-o", "--output-format"},
            description = "Output format: console (default), json, html",
            defaultValue = "console"
    )
    private OutputFormat outputFormat;

    @Option(
            names = {"-f", "--output-file"},
            description = "Output file path (defaults to stdout for console/json, report.html for html)"
    )
    private Path outputFile;

    @Option(
            names = {"-c", "--config"},
            description = "Path to configuration YAML file"
    )
    private Path configFile;

    @Option(
            names = {"-p", "--package-prefix"},
            description = "Package prefix to identify project classes (e.g., com.company). " +
                    "If not specified, will attempt to detect from pom.xml."
    )
    private String packagePrefix;

    @Option(
            names = {"-r", "--risk-threshold"},
            description = "Minimum risk level to report: critical, high, medium, low, info",
            defaultValue = "low"
    )
    private String riskThreshold;

    @Option(
            names = {"-x", "--exclude"},
            description = "Glob patterns to exclude classes from scanning (e.g., '**.Test*', 'com.example.internal.**'). " +
                    "For excluding findings by type, use 'excludePatterns' in state-scan.yaml with regex patterns.",
            split = ","
    )
    private List<String> excludePatterns;

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output"
    )
    private boolean verbose;

    @Option(
            names = {"--fail-on"},
            description = "Exit with non-zero code if findings at this level or higher: critical, high, medium, low",
            defaultValue = "critical"
    )
    private String failOnLevel;

    @Option(
            names = {"--no-color"},
            description = "Disable ANSI colors in console output"
    )
    private boolean noColor;

    public enum OutputFormat {
        console,
        json,
        html
    }

    @Override
    public Integer call() {
        Instant startTime = Instant.now();

        try {
            // Validate project path
            if (!Files.exists(projectPath)) {
                System.err.println("Error: Project path does not exist: " + projectPath);
                return 1;
            }

            if (!Files.isDirectory(projectPath)) {
                System.err.println("Error: Project path is not a directory: " + projectPath);
                return 1;
            }

            // Check for build file
            if (!ClasspathResolverFactory.isSupported(projectPath)) {
                System.err.println("Error: No pom.xml or build.gradle found in: " + projectPath);
                System.err.println("Please specify a Maven or Gradle project directory.");
                return 1;
            }

            // Parse risk levels
            RiskLevel minRisk = parseRiskLevel(riskThreshold, "risk-threshold");
            if (minRisk == null) return 1;

            RiskLevel failLevel = parseRiskLevel(failOnLevel, "fail-on");
            if (failLevel == null) return 1;

            // Print banner (only for console, not json)
            if (outputFormat != OutputFormat.json) {
                printBanner();
            }

            // Load configuration
            LeafTypeConfig leafConfig = loadConfig();

            // Step 1: Resolve classpath
            log("Resolving project dependencies...");
            ClasspathResolver resolver = ClasspathResolverFactory.forProject(projectPath);
            ResolvedClasspath classpath = resolver.resolve(projectPath);

            String effectivePackagePrefix = packagePrefix != null ? packagePrefix : classpath.detectedPackagePrefix();
            log("  Detected package prefix: " + effectivePackagePrefix);
            log("  Found " + classpath.dependencyJars().size() + " dependency JARs");

            // Step 2: Scan bytecode
            log("Scanning bytecode...");
            Set<String> excludes = excludePatterns != null ? Set.copyOf(excludePatterns) : Set.of();
            BytecodeScanner scanner = new BytecodeScanner(effectivePackagePrefix, excludes);
            // Use multi-directory scanning for multi-module Maven projects
            CallGraph fullGraph = scanner.scanMultiple(classpath.allProjectClassesDirs(), classpath.dependencyJars());

            log("  Scanned " + scanner.getClassesScanned() + " classes from " +
                    scanner.getJarsScanned() + " JARs");

            // Step 3: Reachability analysis (tree-shaking)
            log("Analyzing reachability...");
            ReachabilityAnalyzer reachabilityAnalyzer = new ReachabilityAnalyzer(fullGraph, effectivePackagePrefix);
            CallGraph reachableGraph = reachabilityAnalyzer.getReachableGraph();

            log("  " + reachableGraph.classCount() + " reachable classes (of " +
                    fullGraph.classCount() + " total)");

            // Step 4: Run detectors
            log("Detecting stateful patterns...");
            DetectorRegistry detectors = DetectorRegistry.createDefault();
            List<Finding> allFindings = detectors.runAll(reachableGraph, leafConfig);

            // Filter by risk level and exclude patterns
            List<Finding> findings = allFindings.stream()
                    .filter(f -> f.riskLevel().isAtLeast(minRisk))
                    .filter(f -> !shouldExcludeFinding(f, leafConfig))
                    .toList();

            int excludedCount = (int) allFindings.stream()
                    .filter(f -> shouldExcludeFinding(f, leafConfig))
                    .count();

            log("  Found " + findings.size() + " findings (of " + allFindings.size() +
                    " total, filtered to " + minRisk + "+" +
                    (excludedCount > 0 ? ", " + excludedCount + " excluded by pattern" : "") + ")");

            // Build report
            Duration duration = Duration.between(startTime, Instant.now());
            ScanReport report = ScanReport.builder()
                    .projectPath(projectPath.toAbsolutePath())
                    .projectName(projectPath.getFileName().toString())
                    .scanStartTime(startTime)
                    .scanDuration(duration)
                    .classesScanned(scanner.getClassesScanned())
                    .jarsScanned(scanner.getJarsScanned())
                    .findings(findings)
                    .configuration(new ScanReport.ScanConfiguration(
                            effectivePackagePrefix,
                            minRisk,
                            excludePatterns != null ? excludePatterns : List.of()
                    ))
                    .build();

            // Step 5: Output report
            Reporter reporter = createReporter();
            writeReport(report, reporter);

            // Determine exit code
            if (report.hasFindingsAtLeast(failLevel)) {
                if (outputFormat == OutputFormat.console) {
                    System.err.println();
                    System.err.println("Failing due to findings at " + failLevel + " level or higher.");
                }
                return 2;
            }

            return 0;

        } catch (ClasspathResolver.ClasspathResolutionException e) {
            System.err.println("Error resolving dependencies: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private LeafTypeConfig loadConfig() throws IOException {
        LeafTypeConfig defaultConfig = LeafTypeConfig.loadDefault();

        if (configFile != null && Files.exists(configFile)) {
            log("Loading configuration from: " + configFile);
            LeafTypeConfig customConfig = LeafTypeConfig.loadFromFile(configFile);
            return defaultConfig.merge(customConfig);
        }

        // Check for state-scan.yaml in project directory
        Path projectConfig = projectPath.resolve("state-scan.yaml");
        if (Files.exists(projectConfig)) {
            log("Loading configuration from: " + projectConfig);
            LeafTypeConfig customConfig = LeafTypeConfig.loadFromFile(projectConfig);
            return defaultConfig.merge(customConfig);
        }

        return defaultConfig;
    }

    private Reporter createReporter() {
        return switch (outputFormat) {
            case console -> new ConsoleReporter(!noColor);
            case json -> new JsonReporter(true);
            case html -> new HtmlReporter();
        };
    }

    private void writeReport(ScanReport report, Reporter reporter) throws IOException {
        if (outputFile != null) {
            reporter.write(report, outputFile);
            if (outputFormat == OutputFormat.console) {
                System.out.println("Report written to: " + outputFile);
            }
        } else if (outputFormat == OutputFormat.html) {
            // Default HTML output file
            Path htmlFile = projectPath.resolve("state-scan-report.html");
            reporter.write(report, htmlFile);
            System.out.println("Report written to: " + htmlFile);
        } else {
            // Write to stdout
            reporter.write(report, new PrintWriter(new OutputStreamWriter(System.out)));
        }
    }

    private void log(String message) {
        if (verbose && outputFormat != OutputFormat.json) {
            System.out.println(message);
        }
    }

    private RiskLevel parseRiskLevel(String value, String optionName) {
        try {
            return switch (value.toLowerCase()) {
                case "critical" -> RiskLevel.CRITICAL;
                case "high" -> RiskLevel.HIGH;
                case "medium" -> RiskLevel.MEDIUM;
                case "low" -> RiskLevel.LOW;
                case "info" -> RiskLevel.INFO;
                default -> throw new IllegalArgumentException("Unknown risk level: " + value);
            };
        } catch (IllegalArgumentException e) {
            System.err.println("Error: Invalid value for --" + optionName + ": " + value);
            System.err.println("Valid values: critical, high, medium, low, info");
            return null;
        }
    }

    private void printBanner() {
        System.out.println("""
                ╔═══════════════════════════════════════════════════════════════╗
                ║                         STATE-SCAN                            ║
                ║     Stateful Component Detection for Horizontal Scaling       ║
                ╚═══════════════════════════════════════════════════════════════╝
                """);
    }

    /**
     * Checks if a finding should be excluded based on configured exclude patterns.
     * Converts bytecode type descriptor to clean format for matching.
     */
    private boolean shouldExcludeFinding(Finding finding, LeafTypeConfig config) {
        String fieldType = finding.fieldType();
        if (fieldType == null) return false;

        // Convert bytecode descriptor (Lcom/example/Class;) to clean format (com.example.Class)
        String cleanType = extractCleanType(fieldType);
        if (cleanType != null) {
            return config.shouldExcludeType(cleanType);
        }
        return false;
    }

    /**
     * Extracts clean type name from JVM bytecode descriptor.
     * Converts "Lcom/example/Class;" to "com.example.Class".
     */
    private String extractCleanType(String descriptor) {
        if (descriptor == null) return null;

        // Skip array dimensions
        int start = 0;
        while (start < descriptor.length() && descriptor.charAt(start) == '[') {
            start++;
        }

        if (start >= descriptor.length()) return null;

        if (descriptor.charAt(start) == 'L') {
            // Object type: Lcom/example/Class; -> com.example.Class
            int end = descriptor.indexOf(';', start);
            if (end > start) {
                return descriptor.substring(start + 1, end).replace('/', '.');
            }
        }

        return null;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new StateScanCli()).execute(args);
        System.exit(exitCode);
    }
}
