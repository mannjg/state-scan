package com.shared.http;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

/**
 * An HTTP client wrapper with connection pooling.
 * Should be detected as service client type.
 */
public class HttpClientWrapper {

    // HTTP client with internal connection pool - should be detected
    private final CloseableHttpClient httpClient;

    public HttpClientWrapper() {
        this.httpClient = HttpClients.createDefault();
    }

    public CloseableHttpClient getClient() {
        return httpClient;
    }

    public void close() throws Exception {
        httpClient.close();
    }
}
