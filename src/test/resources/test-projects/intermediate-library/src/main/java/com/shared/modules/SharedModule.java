package com.shared.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.shared.cache.SharedCacheService;
import com.shared.data.DatabaseConnectionPool;
import com.shared.http.HttpClientWrapper;

/**
 * A Guice module providing shared stateful services.
 * Should be detected via DI binding analysis.
 */
public class SharedModule extends AbstractModule {

    @Override
    protected void configure() {
        // Bind database pool as singleton
        bind(DatabaseConnectionPool.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public SharedCacheService<String, Object> provideCacheService() {
        return new SharedCacheService<>();
    }

    @Provides
    @Singleton
    public HttpClientWrapper provideHttpClient() {
        return new HttpClientWrapper();
    }
}
