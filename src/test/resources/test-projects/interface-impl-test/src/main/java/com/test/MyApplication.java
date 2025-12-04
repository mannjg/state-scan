package com.test;

/**
 * Application that uses interfaces.
 */
public class MyApplication {

    private final DataService dataService;
    private final AbstractCache cache;

    public MyApplication(DataService dataService, AbstractCache cache) {
        this.dataService = dataService;
        this.cache = cache;
    }

    public void processData(String key) {
        // Use the interface - should be resolved to implementation
        String data = dataService.getData(key);

        // Use the abstract class - should be resolved to MemoryCache
        Object cached = cache.get(key);

        if (cached == null && data != null) {
            cache.put(key, data);
        }
    }
}
