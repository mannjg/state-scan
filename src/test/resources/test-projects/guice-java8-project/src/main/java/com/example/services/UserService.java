package com.example.services;

import com.google.inject.Inject;
import com.shared.cache.SharedCacheService;
import com.shared.data.DatabaseConnectionPool;

/**
 * A service that uses shared stateful components via DI.
 * Should be detected as having paths to stateful components.
 */
public class UserService {

    private final DatabaseConnectionPool connectionPool;
    private final SharedCacheService<String, Object> cacheService;

    @Inject
    public UserService(
            DatabaseConnectionPool connectionPool,
            SharedCacheService<String, Object> cacheService) {
        this.connectionPool = connectionPool;
        this.cacheService = cacheService;
    }

    public Object getUser(String userId) {
        // Check cache first
        Object cached = cacheService.get(userId);
        if (cached != null) {
            return cached;
        }

        // Query from database
        // ... use connectionPool
        return null;
    }
}
