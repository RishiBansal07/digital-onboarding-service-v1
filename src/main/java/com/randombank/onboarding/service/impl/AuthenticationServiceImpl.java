package com.randombank.onboarding.service.impl;

import com.randombank.onboarding.domain.entity.Customer;
import com.randombank.onboarding.exception.UnauthorizedException;
import com.randombank.onboarding.repository.CustomerRepository;
import com.randombank.onboarding.service.AuthenticationService;
import com.randombank.onboarding.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
class AuthenticationServiceImpl implements AuthenticationService {

    /**
     * Deliberately identical for an unknown username and a wrong password,
     * so the API does not reveal which usernames exist.
     */
    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final CustomerRepository customerRepository;
    private final SessionService sessionStore;

    @Override
    public String login(String username, String password) {
        Optional<Customer> customer = customerRepository.findByUsername(username);

        if (customer.isEmpty()) {
            log.debug("Login attempt rejected: invalid credentials");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        // Plain comparison: password encryption is out of scope per the assignment.
        if (!customer.get().getPassword().equals(password)) {
            log.debug("Login attempt rejected: invalid credentials");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        return sessionStore.createSession(username);
    }

    @Override
    public String getUsernameByToken(String token) {
        return sessionStore.getUsernameByToken(token);
    }
}
