package com.example.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.example.modules.AppModule;
import com.example.services.UserService;

/**
 * Main application entry point.
 */
public class Application {

    public static void main(String[] args) {
        Injector injector = Guice.createInjector(new AppModule());
        UserService userService = injector.getInstance(UserService.class);

        // Use the service
        Object user = userService.getUser("user123");
        System.out.println("User: " + user);
    }
}
