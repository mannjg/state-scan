package com.test;

/**
 * Implementation of UtilityInterface.
 */
public class UtilityImpl implements UtilityInterface {

    @Override
    public String process() {
        // This calls the interface's static method
        return UtilityInterface.helper1();
    }
}
