package io.statescan.model;

/**
 * Categories of state that can be detected.
 */
public enum StateType {
    /**
     * State held in memory within the JVM.
     * Examples: singleton fields, static fields, caches.
     */
    IN_MEMORY("In-Memory State"),

    /**
     * State managed by external systems.
     * Examples: database, message broker, external cache.
     */
    EXTERNAL("External State"),

    /**
     * State related to service client connections.
     * Examples: HTTP client pools, gRPC channels.
     */
    SERVICE_CLIENT("Service Client"),

    /**
     * State related to resilience patterns.
     * Examples: circuit breakers, retry state, bulkheads.
     */
    RESILIENCE("Resilience State"),

    /**
     * Session or request-scoped state.
     * Examples: session-scoped beans, ThreadLocal.
     */
    SESSION("Session State");

    private final String displayName;

    StateType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
