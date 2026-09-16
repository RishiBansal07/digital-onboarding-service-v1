package com.randombank.onboarding.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PasswordGeneratorTest {

    @Test
    void generateDefaultPassword() {
        String password = PasswordGenerator.generateDefaultPassword();

        assertNotNull(password);
        assertEquals(12, password.length());
    }

    @Test
    void generateDefaultPasswordUnique() {
        String password1 = PasswordGenerator.generateDefaultPassword();
        String password2 = PasswordGenerator.generateDefaultPassword();

        assertNotEquals(password1, password2);
    }

    @Test
    void generateDefaultPasswordMultipleTimes() {
        for (int i = 0; i < 10; i++) {
            String password = PasswordGenerator.generateDefaultPassword();
            assertEquals(12, password.length());
            assertNotNull(password);
        }
    }
}

