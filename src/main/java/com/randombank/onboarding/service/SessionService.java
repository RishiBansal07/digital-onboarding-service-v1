package com.randombank.onboarding.service;

/**
 * Stores active sessions as a mapping from opaque token to username.
 *
 * <p>Kept separate from {@link AuthenticationService} so the storage strategy can change
 * — for example to Redis or JWT — without touching authentication logic.
 */
public interface SessionService {

    /**
     * Opens a session for the given customer.
     *
     * @return a newly issued opaque token
     */
    String createSession(String username);

    /**
     * Resolves the username associated with a token.
     *
     * @throws com.randombank.onboarding.exception.UnauthorizedException if the token is unknown
     */
    String getUsernameByToken(String token);
}

