package com.example.modules;

import com.google.inject.AbstractModule;

/**
 * Alternate application module that installs ExtendedModule.
 *
 * This demonstrates a two-level inheritance chain:
 *   AlternateAppModule -> install(ExtendedModule) -> extends SharedModule
 *
 * The DI binding analysis should trace through this chain to find
 * stateful components in SharedModule (from intermediate-library).
 */
public class AlternateAppModule extends AbstractModule {

    @Override
    protected void configure() {
        // Install ExtendedModule which extends SharedModule
        // This creates a module chain that should be traced
        install(new ExtendedModule());
    }
}
