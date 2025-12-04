package com.example.cdi.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.example.cdi.data.InMemoryCache;

/**
 * Implementation of DataRepository using in-memory cache.
 * Should be detected via single-impl interface binding.
 */
@ApplicationScoped
public class InMemoryDataRepository implements DataRepository {

    private final InMemoryCache cache;

    @Inject
    public InMemoryDataRepository(InMemoryCache cache) {
        this.cache = cache;
    }

    @Override
    public Object findById(String id) {
        return cache.get(id);
    }

    @Override
    public void save(Object entity) {
        String id = entity.toString(); // simplified
        cache.put(id, entity);
    }
}
