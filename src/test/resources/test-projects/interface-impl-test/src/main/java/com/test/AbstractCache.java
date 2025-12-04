package com.test;

/**
 * Abstract cache class.
 */
public abstract class AbstractCache {

    public abstract Object get(String key);

    public abstract void put(String key, Object value);

    public void clear() {
        // Default implementation
    }
}
