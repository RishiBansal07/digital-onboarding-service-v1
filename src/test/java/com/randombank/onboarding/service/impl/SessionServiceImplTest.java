package com.randombank.onboarding.service.impl;

import com.randombank.onboarding.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceImplTest {

    private SessionServiceImpl sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionServiceImpl();
    }

    @Test
    void createSessionReturnsToken() {
        String username = "john_doe";

        String token = sessionService.createSession(username);

        assertNotNull(token);
        assertFalse(token.isBlank());
        // Token should be a valid UUID
        assertDoesNotThrow(() -> UUID.fromString(token));
    }

    @Test
    void createSessionStoresTokenUsernameMapping() {
        String username = "john_doe";

        String token = sessionService.createSession(username);
        String retrievedUsername = sessionService.getUsernameByToken(token);

        assertEquals(username, retrievedUsername);
    }

    @Test
    void getUsernameByValidTokenReturnsUsername() {
        String username = "alice_smith";
        String token = sessionService.createSession(username);

        String retrievedUsername = sessionService.getUsernameByToken(token);

        assertEquals(username, retrievedUsername);
    }

    @Test
    void getUsernameByInvalidTokenThrowsUnauthorizedException() {
        String invalidToken = "not-a-valid-token-" + UUID.randomUUID();

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                sessionService.getUsernameByToken(invalidToken)
        );

        assertEquals("Invalid or expired token", exception.getMessage());
    }

    @Test
    void getUsernameByNullTokenThrowsNullPointerException() {
        // ConcurrentHashMap.get(null) throws NullPointerException, which is acceptable behavior
        assertThrows(NullPointerException.class, () ->
                sessionService.getUsernameByToken(null)
        );
    }

    @Test
    void multipleSessionsCanBeCreatedForDifferentUsers() {
        String user1 = "user_one";
        String user2 = "user_two";

        String token1 = sessionService.createSession(user1);
        String token2 = sessionService.createSession(user2);

        assertNotEquals(token1, token2);
        assertEquals(user1, sessionService.getUsernameByToken(token1));
        assertEquals(user2, sessionService.getUsernameByToken(token2));
    }

    @Test
    void sameUserCanCreateMultipleSessions() {
        String username = "multi_session_user";

        String token1 = sessionService.createSession(username);
        String token2 = sessionService.createSession(username);

        assertNotEquals(token1, token2);
        assertEquals(username, sessionService.getUsernameByToken(token1));
        assertEquals(username, sessionService.getUsernameByToken(token2));
    }

    @Test
    void tokenPersistsAcrossMultipleLookups() {
        String username = "persistent_user";
        String token = sessionService.createSession(username);

        String retrieved1 = sessionService.getUsernameByToken(token);
        String retrieved2 = sessionService.getUsernameByToken(token);
        String retrieved3 = sessionService.getUsernameByToken(token);

        assertEquals(username, retrieved1);
        assertEquals(username, retrieved2);
        assertEquals(username, retrieved3);
    }
}

