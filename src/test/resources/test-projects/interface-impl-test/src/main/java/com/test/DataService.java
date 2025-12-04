package com.test;

/**
 * Interface for data service operations.
 */
public interface DataService {
    String getData(String key);
    void putData(String key, String value);
}
