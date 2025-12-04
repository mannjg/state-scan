package com.example.modules;

import com.google.inject.AbstractModule;
import com.shared.modules.SharedModule;

/**
 * Main application module that installs shared module.
 * This creates a DI binding chain to stateful components.
 */
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        // Install shared module with stateful components
        install(new SharedModule());
    }
}
