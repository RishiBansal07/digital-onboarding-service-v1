package com.randombank.onboarding.util;

import com.randombank.onboarding.exception.UnauthorizedException;

/**
 * Utility for extracting and validating Bearer tokens from Authorization headers.
 */
public class AuthorizationUtil {

    private static final String BEARER_PREFIX = "Bearer ";

    private AuthorizationUtil() {
        // Utility class, no instantiation
    }

    /**
     * Extracts a Bearer token from an Authorization header.
     *
     * @param authorizationHeader the raw Authorization header value
     * @return the token value (without "Bearer " prefix)
     * @throws UnauthorizedException if the header is missing, malformed, or doesn't use Bearer scheme
     */
    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new UnauthorizedException("Authorization header is missing");
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Authorization header must use Bearer token");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("Bearer token is missing");
        }

        return token;
    }
}

