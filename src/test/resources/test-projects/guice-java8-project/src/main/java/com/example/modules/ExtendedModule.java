package com.example.modules;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.shared.modules.SharedModule;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Module that extends SharedModule (from intermediate library).
 * This tests module inheritance chain detection - bindings from
 * SharedModule should be discovered through this extension.
 */
public class ExtendedModule extends SharedModule {

    // Additional static state in the extended module
    private static final Map<String, Object> moduleState = new ConcurrentHashMap<>();

    @Override
    protected void configure() {
        // Call parent configuration - this brings in all SharedModule bindings
        super.configure();

        // Additional bindings specific to this module
        bind(SessionManager.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public RequestCounter provideRequestCounter() {
        return new RequestCounter();
    }

    /**
     * Session manager with mutable state.
     */
    public static class SessionManager {
        private final Map<String, Object> sessions = new ConcurrentHashMap<>();

        public void createSession(String id, Object data) {
            sessions.put(id, data);
        }

        public Object getSession(String id) {
            return sessions.get(id);
        }

        public void invalidateSession(String id) {
            sessions.remove(id);
        }
    }

    /**
     * Request counter with mutable state.
     */
    public static class RequestCounter {
        private long count = 0;

        public synchronized long increment() {
            return ++count;
        }

        public synchronized long getCount() {
            return count;
        }
    }
}
