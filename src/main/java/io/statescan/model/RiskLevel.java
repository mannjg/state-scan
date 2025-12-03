package io.statescan.model;

/**
 * Risk levels for stateful component findings.
 * Higher risk = more likely to cause issues when horizontally scaling.
 */
public enum RiskLevel {
    /**
     * Critical risk - will almost certainly cause issues.
     * Examples: mutable static fields, singleton with accumulating state.
     */
    CRITICAL(1, "CRITICAL"),

    /**
     * High risk - likely to cause issues.
     * Examples: ThreadLocal, session-scoped beans, circuit breaker state.
     */
    HIGH(2, "HIGH"),

    /**
     * Medium risk - may cause issues depending on usage.
     * Examples: database connections, message broker clients.
     */
    MEDIUM(3, "MEDIUM"),

    /**
     * Low risk - unlikely to cause issues but worth noting.
     * Examples: request-scoped beans, properly pooled connections.
     */
    LOW(4, "LOW"),

    /**
     * Informational - no risk, but documented for completeness.
     * Examples: immutable singletons, configuration holders.
     */
    INFO(5, "INFO");

    private final int severity;
    private final String label;

    RiskLevel(int severity, String label) {
        this.severity = severity;
        this.label = label;
    }

    public int severity() {
        return severity;
    }

    public String label() {
        return label;
    }

    /**
     * Returns true if this risk level is at least as severe as the given threshold.
     */
    public boolean isAtLeast(RiskLevel threshold) {
        return this.severity <= threshold.severity;
    }
}
