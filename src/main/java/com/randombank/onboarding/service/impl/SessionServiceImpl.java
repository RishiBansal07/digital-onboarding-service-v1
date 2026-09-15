package com.randombank.onboarding.service.impl;

import com.randombank.onboarding.exception.UnauthorizedException;
import com.randombank.onboarding.service.SessionService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store.
 *
 * <p>Sessions are lost on restart and are not shared between instances. A distributed
 * store would be required to run more than one instance.
 */
@Service
class SessionServiceImpl implements SessionService {

    private final Map<String, String> tokenToUsername = new ConcurrentHashMap<>();

    @Override
    public String createSession(String username) {
        String token = UUID.randomUUID().toString();
        tokenToUsername.put(token, username);
        return token;
    }

    @Override
    public String getUsernameByToken(String token) {
        String username = tokenToUsername.get(token);

        if (username == null) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        return username;
    }
}

