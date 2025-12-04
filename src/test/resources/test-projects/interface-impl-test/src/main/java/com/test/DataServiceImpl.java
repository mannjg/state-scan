package com.test;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of DataService.
 */
public class DataServiceImpl implements DataService {

    private final Map<String, String> data = new HashMap<>();

    @Override
    public String getData(String key) {
        return data.get(key);
    }

    @Override
    public void putData(String key, String value) {
        data.put(key, value);
    }
}
