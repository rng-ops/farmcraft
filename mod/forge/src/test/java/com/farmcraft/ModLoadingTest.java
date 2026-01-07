package com.farmcraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests to ensure the mod loads correctly.
 */
public class ModLoadingTest {

    @Test
    public void testModIdIsCorrect() {
        assertEquals("farmcraft", FarmCraft.MOD_ID, 
            "Mod ID should be 'farmcraft'");
    }

    @Test
    public void testLoggerExists() {
        assertNotNull(FarmCraft.LOGGER, 
            "Logger should be initialized");
    }

    @Test
    public void testModClassExists() {
        // Simple test to ensure the main mod class is loadable
        assertNotNull(FarmCraft.class, 
            "FarmCraft main class should exist");
    }
}
