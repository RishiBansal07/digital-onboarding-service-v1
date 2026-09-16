package com.randombank.onboarding.util;

import com.randombank.onboarding.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationUtilTest {

    @Test
    void extractBearerTokenWithValidHeader() {
        String header = "Bearer valid-token-12345";
        String token = AuthorizationUtil.extractBearerToken(header);
        assertEquals("valid-token-12345", token);
    }

    @Test
    void extractBearerTokenTrimsWhitespace() {
        String header = "Bearer   token-with-spaces  ";
        String token = AuthorizationUtil.extractBearerToken(header);
        assertEquals("token-with-spaces", token);
    }

    @Test
    void extractBearerTokenThrowsExceptionWhenHeaderIsNull() {
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                AuthorizationUtil.extractBearerToken(null)
        );
        assertEquals("Authorization header is missing", exception.getMessage());
    }

    @Test
    void extractBearerTokenThrowsExceptionWhenHeaderIsBlank() {
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                AuthorizationUtil.extractBearerToken("   ")
        );
        assertEquals("Authorization header is missing", exception.getMessage());
    }

    @Test
    void extractBearerTokenThrowsExceptionWhenNotBearerScheme() {
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                AuthorizationUtil.extractBearerToken("Basic dXNlcjpwYXNz")
        );
        assertEquals("Authorization header must use Bearer token", exception.getMessage());
    }

    @Test
    void extractBearerTokenThrowsExceptionWhenBearerPrefixButNoToken() {
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                AuthorizationUtil.extractBearerToken("Bearer ")
        );
        assertEquals("Bearer token is missing", exception.getMessage());
    }

    @Test
    void extractBearerTokenThrowsExceptionWhenBearerPrefixButOnlyWhitespace() {
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                AuthorizationUtil.extractBearerToken("Bearer    ")
        );
        assertEquals("Bearer token is missing", exception.getMessage());
    }

    @Test
    void extractBearerTokenHandlesUUIDToken() {
        String header = "Bearer 7f55c9fa-2af5-41eb-ad0c-ef8a336ee50f";
        String token = AuthorizationUtil.extractBearerToken(header);
        assertEquals("7f55c9fa-2af5-41eb-ad0c-ef8a336ee50f", token);
    }
}

