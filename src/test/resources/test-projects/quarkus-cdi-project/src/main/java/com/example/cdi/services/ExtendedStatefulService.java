package com.example.cdi.services;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * CDI bean that extends a stateful base class.
 *
 * This demonstrates inheritance-based state detection:
 *   ExtendedStatefulService -> extends BaseStatefulService (has ThreadLocal + Map state)
 *
 * The reachability analysis should detect the inherited stateful
 * components from the base class.
 */
@ApplicationScoped
public class ExtendedStatefulService extends BaseStatefulService {

    public void processRequest(String requestId, Object data) {
        // Set request context (uses inherited ThreadLocal)
        setRequestContext(requestId);

        try {
            // Cache data (uses inherited Map)
            cacheState(requestId, data);

            // Process...
            doProcessing(data);
        } finally {
            // Clean up
            clearRequestContext();
        }
    }

    private void doProcessing(Object data) {
        // Business logic here
        String context = getRequestContext();
        System.out.println("Processing in context: " + context);
    }

    public Object getProcessedData(String requestId) {
        return getCachedState(requestId);
    }
}
