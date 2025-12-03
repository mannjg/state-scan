# State-Scan

Detects stateful components in Java applications that may cause issues when horizontally scaling.

## Quick Start

```bash
java -jar state-scan.jar /path/to/maven/project
```

## Options

```
-o, --output-format   console | json | html (default: console)
-f, --output-file     Output file path
-p, --package-prefix  Filter to specific package (auto-detected from pom.xml)
-r, --risk-threshold  critical | high | medium | low | info (default: low)
-c, --config          Custom config YAML
-x, --exclude         Glob patterns to exclude classes (e.g., '**.Test*')
-v, --verbose         Show progress
--fail-on             Exit non-zero at this level (default: critical)
--no-color            Disable ANSI colors
```

## Detectors

| Detector | Description |
|----------|-------------|
| Static State | Mutable static fields (maps, lists, sets) |
| Singleton | @Singleton/@Component with mutable instance fields |
| External State | Redis, database connections, message queues |
| Cache | Caffeine, Guava, and other cache implementations |
| ThreadLocal | ThreadLocal fields that may leak across requests |
| Resilience | Circuit breakers, rate limiters with local state |
| File State | Local file system dependencies |
| Service Client | HTTP/gRPC clients that may hold state |

## Exclude Patterns

Create `state-scan.yaml` in your project root:

```yaml
# Exclude findings by fully-qualified type (regex)
excludePatterns:
  - '.*\.prometheus\.client\.(Counter|Gauge|Histogram|Summary)$'
  - '.*\.micrometer\.core\.instrument\..*'
  - '.*Test$'
```

## CI/CD Integration

```bash
# JSON output for parsing
java -jar state-scan.jar /path/to/project -o json -f findings.json

# Fail build on critical findings
java -jar state-scan.jar /path/to/project --fail-on critical

# Exit codes:
#   0 = success
#   1 = error
#   2 = findings at --fail-on level or higher
```

## Building

```bash
mvn clean package
# JAR at target/state-scan-1.0-SNAPSHOT-jar-with-dependencies.jar
```
