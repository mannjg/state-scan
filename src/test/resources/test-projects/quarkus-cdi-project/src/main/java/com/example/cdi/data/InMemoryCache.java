package com.example.cdi.data;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Application-scoped cache with in-memory state.
 * Should be detected as singleton with mutable state.
 */
@ApplicationScoped
public class InMemoryCache {

    // Mutable state in application-scoped bean
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public Object get(String key) {
        return cache.get(key);
    }

    public void put(String key, Object value) {
        cache.put(key, value);
    }

    public void evict(String key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
