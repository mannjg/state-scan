package com.example.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Service that uses SLF4J logging to test tree-shaking behavior.
 * <p>
 * The scanner should detect that this class uses LoggerFactory, but should NOT
 * report paths that start with logback internal classes like ClassicConstants,
 * MarkerFactory, etc. All reported paths should start with com.example.* classes.
 */
public class LoggingService {

    private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);

    public void processRequest(String requestId) {
        // Use MDC which internally uses ThreadLocal - but this should be reported
        // as a path starting from LoggingService, not from logback internals
        MDC.put("requestId", requestId);
        try {
            logger.info("Processing request: {}", requestId);
            doWork();
        } finally {
            MDC.remove("requestId");
        }
    }

    private void doWork() {
        logger.debug("Doing work...");
    }
}
