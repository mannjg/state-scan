package com.test;

/**
 * Interface with static methods - static methods are NOT polymorphic.
 */
public interface UtilityInterface {

    static String helper1() {
        // Calling another static method on same interface
        return helper2();
    }

    static String helper2() {
        return "helper2";
    }

    // Instance method that DOES need resolution
    String process();
}
