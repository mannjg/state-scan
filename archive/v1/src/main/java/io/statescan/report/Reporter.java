package io.statescan.report;

import io.statescan.model.ScanReport;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;

/**
 * Interface for report output formatters.
 */
public interface Reporter {

    /**
     * Returns the format name (e.g., "console", "json", "html").
     */
    String format();

    /**
     * Writes the report to the given writer.
     */
    void write(ScanReport report, Writer writer) throws IOException;

    /**
     * Writes the report to the given file path.
     */
    default void write(ScanReport report, Path path) throws IOException {
        try (var writer = java.nio.file.Files.newBufferedWriter(path)) {
            write(report, writer);
        }
    }

    /**
     * Returns the report as a string.
     */
    default String toString(ScanReport report) {
        try {
            var writer = new java.io.StringWriter();
            write(report, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate report", e);
        }
    }
}
