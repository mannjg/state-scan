package com.shared.data;

import java.sql.Connection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * A shared connection pool with stateful components.
 * This should be detected via path-based analysis.
 */
public class DatabaseConnectionPool {

    // Static mutable state - should be detected
    private static final Map<String, Connection> connectionCache = new ConcurrentHashMap<>();

    // Instance-level mutable state
    private int maxConnections = 10;
    private int activeConnections = 0;

    public Connection getConnection(String key) {
        return connectionCache.get(key);
    }

    public void releaseConnection(String key) {
        connectionCache.remove(key);
        activeConnections--;
    }

    public int getActiveCount() {
        return activeConnections;
    }
}
