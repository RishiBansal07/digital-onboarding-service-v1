package com.randombank.onboarding.controller;

import com.randombank.onboarding.dto.response.ErrorResponse;
import com.randombank.onboarding.exception.BadRequestException;
import com.randombank.onboarding.exception.ConflictException;
import com.randombank.onboarding.exception.NotFoundException;
import com.randombank.onboarding.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleConflictException() {
        ResponseEntity<ErrorResponse> response = handler.handleConflict(new ConflictException("Conflict"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", response.getBody().code());
        assertEquals("Conflict", response.getBody().message());
    }

    @Test
    void handleUnauthorizedException() {
        ResponseEntity<ErrorResponse> response = handler.handleUnauthorized(new UnauthorizedException("Unauthorized"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHORIZED", response.getBody().code());
        assertEquals("Unauthorized", response.getBody().message());
    }

    @Test
    void handleNotFoundException() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new NotFoundException("Not found"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOT_FOUND", response.getBody().code());
        assertEquals("Not found", response.getBody().message());
    }

    @Test
    void handleBadRequestException() {
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(new BadRequestException("Bad request"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("BAD_REQUEST", response.getBody().code());
        assertEquals("Bad request", response.getBody().message());
    }

    @Test
    void handleGenericException() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(new Exception("Unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertEquals("Unexpected error occurred", response.getBody().message());
        assertEquals(null, response.getBody().details());
    }

    @Test
    void errorResponseHasTimestamp() {
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(new BadRequestException("Error"));

        assertNotNull(response.getBody().timestamp());
        assertEquals(true, response.getBody().timestamp() > 0);
    }
}
