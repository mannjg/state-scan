package com.example.cdi.services;

/**
 * Interface for data repository.
 * Has a single implementation - should be bound via CDI discovery.
 */
public interface DataRepository {
    Object findById(String id);
    void save(Object entity);
}
