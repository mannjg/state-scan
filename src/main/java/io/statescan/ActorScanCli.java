package io.statescan;

import io.statescan.analysis.CallGraphBuilder;
import io.statescan.bytecode.ProjectScanner;
import io.statescan.model.CallGraph;
import io.statescan.model.ScanResult;
import io.statescan.output.CallGraphOutput;
import io.statescan.output.ConsoleOutput;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI entry point for the Actor Scanner.
 * <p>
 * Scans Java bytecode to extract class -> method -> actor relationships.
 */
@Command(
    name = "actor-scan",
    mixinStandardHelpOptions = true,
    version = "actor-scan 2.0.0",
    description = "Scan Java bytecode to extract class -> method -> actor relationships."
)
public class ActorScanCli implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to scan (directory with target/classes, or JAR file)")
    private Path projectPath;

    @Option(names = {"-p", "--packages"},
            description = "Package prefixes to include (comma-separated, e.g., 'com.example,org.myapp')")
    private String packagePrefixes;

    @Option(names = {"-x", "--exclude-packages"},
            description = "Package prefixes to exclude (comma-separated, takes precedence over includes)")
    private String excludePackages;

    @Option(names = {"--show-empty"},
            description = "Show methods with no actors")
    private boolean showEmpty = false;

    @Option(names = {"--public-only"},
            description = "Show only public methods")
    private boolean publicOnly = false;

    @Option(names = {"--maven"},
            description = "Scan as Maven multi-module project")
    private boolean mavenProject = false;

    @Option(names = {"--root-packages"},
            description = "Additional package prefixes to scan from transitive dependencies (comma-separated, requires --maven)")
    private String rootPackages;

    @Option(names = {"--callgraph"},
            description = "Build and display callgraph with parameter flow")
    private boolean showCallgraph = false;

    @Option(names = {"--no-color"},
            description = "Disable colored output")
    private boolean noColor = false;

    @Override
    public Integer call() throws Exception {
        // Validate path
        if (!Files.exists(projectPath)) {
            System.err.println("Error: Path does not exist: " + projectPath);
            return 1;
        }

        // Parse package prefixes
        Set<String> packages = Set.of();
        if (packagePrefixes != null && !packagePrefixes.isBlank()) {
            packages = Arrays.stream(packagePrefixes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        }

        // Parse exclude packages
        Set<String> excludePkgs = Set.of();
        if (excludePackages != null && !excludePackages.isBlank()) {
            excludePkgs = Arrays.stream(excludePackages.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        }

        // Parse root packages for dependency scanning
        Set<String> rootPkgs = Set.of();
        if (rootPackages != null && !rootPackages.isBlank()) {
            rootPkgs = Arrays.stream(rootPackages.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        }

        // Validate: --root-packages requires --maven
        if (!rootPkgs.isEmpty() && !mavenProject) {
            System.err.println("Error: --root-packages requires --maven mode");
            return 1;
        }

        // Create scanner
        ProjectScanner scanner = new ProjectScanner(packages, excludePkgs, rootPkgs);

        // Scan
        ScanResult result;
        if (mavenProject) {
            result = scanner.scanMavenProject(projectPath);
        } else {
            result = scanner.scan(projectPath);
        }

        // Output results
        boolean useColor = !noColor;

        if (showCallgraph) {
            // Build callgraph
            Set<String> callgraphPackages = new HashSet<>();
            if (!packages.isEmpty()) {
                callgraphPackages.addAll(packages);
            }
            if (!rootPkgs.isEmpty()) {
                callgraphPackages.addAll(rootPkgs);
            }

            CallGraphBuilder builder = new CallGraphBuilder(result, callgraphPackages);
            CallGraph callGraph = builder.build();

            // Output callgraph
            CallGraphOutput cgOutput = new CallGraphOutput(callGraph, result, System.out, useColor);
            cgOutput.printFull();
        } else {
            // Standard actor output
            ConsoleOutput output = new ConsoleOutput()
                .showEmptyMethods(showEmpty)
                .showOnlyPublicMethods(publicOnly)
                .useColor(useColor);

            output.print(result);
        }

        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ActorScanCli()).execute(args);
        System.exit(exitCode);
    }
}
