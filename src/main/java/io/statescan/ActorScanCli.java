package io.statescan;

import io.statescan.bytecode.ProjectScanner;
import io.statescan.model.ScanResult;
import io.statescan.output.ConsoleOutput;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

    @Option(names = {"--show-empty"},
            description = "Show methods with no actors")
    private boolean showEmpty = false;

    @Option(names = {"--public-only"},
            description = "Show only public methods")
    private boolean publicOnly = false;

    @Option(names = {"--maven"},
            description = "Scan as Maven multi-module project")
    private boolean mavenProject = false;

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

        // Create scanner
        ProjectScanner scanner = new ProjectScanner(packages);

        // Scan
        ScanResult result;
        if (mavenProject) {
            result = scanner.scanMavenProject(projectPath);
        } else {
            result = scanner.scan(projectPath);
        }

        // Output results
        ConsoleOutput output = new ConsoleOutput()
            .showEmptyMethods(showEmpty)
            .showOnlyPublicMethods(publicOnly);

        output.print(result);

        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ActorScanCli()).execute(args);
        System.exit(exitCode);
    }
}
