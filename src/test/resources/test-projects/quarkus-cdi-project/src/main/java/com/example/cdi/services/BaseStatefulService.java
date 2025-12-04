package com.example.cdi.services;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Base class with stateful components.
 * Simulates a base class from an intermediate library.
 * Subclasses inherit this state.
 */
public abstract class BaseStatefulService {

    // Protected mutable state inherited by subclasses
    protected final Map<String, Object> stateCache = new ConcurrentHashMap<>();

    // Thread-local for request context
    protected static final ThreadLocal<String> requestContext = new ThreadLocal<>();

    protected void cacheState(String key, Object value) {
        stateCache.put(key, value);
    }

    protected Object getCachedState(String key) {
        return stateCache.get(key);
    }

    protected void setRequestContext(String context) {
        requestContext.set(context);
    }

    protected String getRequestContext() {
        return requestContext.get();
    }

    protected void clearRequestContext() {
        requestContext.remove();
    }
}
