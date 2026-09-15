package com.randombank.onboarding.service;

public interface AuthenticationService {

    /**
     * Verifies credentials and opens a session.
     *
     * @return an opaque session token
     * @throws com.randombank.onboarding.exception.UnauthorizedException if the credentials are invalid
     */
    String login(String username, String password);

    /**
     * Resolves the username behind a session token. Performs no database access.
     *
     * @throws com.randombank.onboarding.exception.UnauthorizedException if the token is unknown
     */
    String getUsernameByToken(String token);
}
