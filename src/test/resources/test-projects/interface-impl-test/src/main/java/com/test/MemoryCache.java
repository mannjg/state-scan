package com.test;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory cache implementation.
 */
public class MemoryCache extends AbstractCache {

    private final Map<String, Object> cache = new HashMap<>();

    @Override
    public Object get(String key) {
        return cache.get(key);
    }

    @Override
    public void put(String key, Object value) {
        cache.put(key, value);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
