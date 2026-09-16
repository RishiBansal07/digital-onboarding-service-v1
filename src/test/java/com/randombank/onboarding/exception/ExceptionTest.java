package com.randombank.onboarding.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExceptionTest {

    @Test
    void badRequestException() {
        BadRequestException ex = new BadRequestException("Invalid request");
        assertEquals("Invalid request", ex.getMessage());
    }

    @Test
    void conflictException() {
        ConflictException ex = new ConflictException("Username already exists");
        assertEquals("Username already exists", ex.getMessage());
    }

    @Test
    void notFoundException() {
        NotFoundException ex = new NotFoundException("Account not found");
        assertEquals("Account not found", ex.getMessage());
    }

    @Test
    void unauthorizedException() {
        UnauthorizedException ex = new UnauthorizedException("Invalid token");
        assertEquals("Invalid token", ex.getMessage());
    }

    @Test
    void exceptionIsRuntimeException() {
        assertThrows(RuntimeException.class, () -> {
            throw new BadRequestException("test");
        });
    }
}

