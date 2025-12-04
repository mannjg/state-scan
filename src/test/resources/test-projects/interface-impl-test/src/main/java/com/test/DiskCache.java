package com.test;

/**
 * Disk-based cache implementation - creates ambiguity with MemoryCache.
 */
public class DiskCache extends AbstractCache {

    @Override
    public Object get(String key) {
        // Read from disk
        return null;
    }

    @Override
    public void put(String key, Object value) {
        // Write to disk
    }
}
