package com.example.cdi.producers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;

/**
 * CDI producer for HTTP client.
 * Should be detected via @Produces annotation.
 */
@ApplicationScoped
public class HttpClientProducer {

    @Produces
    @Singleton
    public HttpClient produceHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
