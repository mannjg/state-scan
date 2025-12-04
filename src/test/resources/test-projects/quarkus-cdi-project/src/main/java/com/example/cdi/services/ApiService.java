package com.example.cdi.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.http.HttpClient;

/**
 * Service that uses injected HTTP client.
 * Should show path to HTTP client via CDI.
 */
@ApplicationScoped
public class ApiService {

    private final HttpClient httpClient;
    private final DataRepository dataRepository;

    @Inject
    public ApiService(HttpClient httpClient, DataRepository dataRepository) {
        this.httpClient = httpClient;
        this.dataRepository = dataRepository;
    }

    public Object fetchAndStore(String url) {
        // Use httpClient to fetch data
        // Store in repository
        Object data = "fetched data from " + url;
        dataRepository.save(data);
        return data;
    }
}
