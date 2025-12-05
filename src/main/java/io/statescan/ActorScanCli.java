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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
    name = "actor-scan",
    mixinStandardHelpOptions = true,
    version = "actor-scan 2.0.0",
    description = "Scan Java bytecode to extract class -> method -> actor relationships."
)
public class ActorScanCli implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, required = true,
            description = "Path to YAML configuration file")
    private Path configPath;

    @Option(names = {"--show-empty"},
            description = "Show methods with no actors")
    private boolean showEmpty = false;

    @Option(names = {"--public-only"},
            description = "Show only public methods")
    private boolean publicOnly = false;

    @Option(names = {"--maven"},
            description = "Scan as Maven multi-module project")
    private boolean mavenProject = false;

    @Option(names = {"--callgraph"},
            description = "Build and display callgraph with parameter flow")
    private boolean showCallgraph = false;

    @Option(names = {"--no-color"},
            description = "Disable colored output")
    private boolean noColor = false;

    @Override
    public Integer call() throws Exception {
        // Load config
        if (!Files.exists(configPath)) {
            System.err.println("Error: Config file does not exist: " + configPath);
            return 1;
        }

        ScanConfig config;
        try {
            config = ScanConfig.load(configPath);
        } catch (Exception e) {
            System.err.println("Error loading config: " + e.getMessage());
            return 1;
        }

        // Validate project path
        Path projectPath = config.getProjectPath();
        if (!Files.exists(projectPath)) {
            System.err.println("Error: Project path does not exist: " + projectPath);
            return 1;
        }

        // Validate: rootPackages implies maven mode
        if (config.hasRootPackages() && !mavenProject) {
            System.err.println("Warning: rootPackages configured but --maven not specified. Enabling maven mode.");
            mavenProject = true;
        }

        // Create scanner
        ProjectScanner scanner = new ProjectScanner(config);

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
            if (!config.getPackages().isEmpty()) {
                callgraphPackages.addAll(config.getPackages());
            }
            if (!config.getRootPackages().isEmpty()) {
                callgraphPackages.addAll(config.getRootPackages());
            }

            CallGraphBuilder builder = new CallGraphBuilder(result, callgraphPackages, config);
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
