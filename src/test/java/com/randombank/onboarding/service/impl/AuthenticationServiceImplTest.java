package com.randombank.onboarding.service.impl;

import com.randombank.onboarding.domain.entity.Customer;
import com.randombank.onboarding.exception.UnauthorizedException;
import com.randombank.onboarding.repository.CustomerRepository;
import com.randombank.onboarding.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private SessionService sessionService;

    private AuthenticationServiceImpl authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationServiceImpl(
                customerRepository,
                sessionService
        );
    }

    @Test
    void loginWithValidCredentialsReturnsToken() {
        String username = "john_doe";
        String password = "correct_password";
        String expectedToken = "token-uuid-123";

        Customer customer = new Customer(
                "John Doe",
                "Damrak 1, Amsterdam",
                username,
                LocalDate.of(1990, 5, 20),
                "NL",
                password
        );

        when(customerRepository.findByUsername(username)).thenReturn(Optional.of(customer));
        when(sessionService.createSession(username)).thenReturn(expectedToken);

        String token = authenticationService.login(username, password);

        assertEquals(expectedToken, token);
        verify(customerRepository).findByUsername(username);
        verify(sessionService).createSession(username);
    }

    @Test
    void loginWithWrongPasswordThrowsUnauthorizedException() {
        String username = "john_doe";
        String correctPassword = "correct_password";
        String wrongPassword = "wrong_password";

        Customer customer = new Customer(
                "John Doe",
                "Damrak 1, Amsterdam",
                username,
                LocalDate.of(1990, 5, 20),
                "NL",
                correctPassword
        );

        when(customerRepository.findByUsername(username)).thenReturn(Optional.of(customer));

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                authenticationService.login(username, wrongPassword)
        );

        assertEquals("Invalid username or password", exception.getMessage());
        verify(sessionService, never()).createSession(anyString());
    }

    @Test
    void loginWithNonExistentUsernameThrowsUnauthorizedException() {
        String username = "non_existent";
        String password = "any_password";

        when(customerRepository.findByUsername(username)).thenReturn(Optional.empty());

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                authenticationService.login(username, password)
        );

        assertEquals("Invalid username or password", exception.getMessage());
        verify(sessionService, never()).createSession(anyString());
    }

    @Test
    void getUsernameByTokenReturnsUsername() {
        String token = "valid-token";
        String expectedUsername = "john_doe";

        when(sessionService.getUsernameByToken(token)).thenReturn(expectedUsername);

        String username = authenticationService.getUsernameByToken(token);

        assertEquals(expectedUsername, username);
        verify(sessionService).getUsernameByToken(token);
    }

    @Test
    void getUsernameByInvalidTokenThrowsUnauthorizedException() {
        String invalidToken = "invalid-token";

        when(sessionService.getUsernameByToken(invalidToken))
                .thenThrow(new UnauthorizedException("Invalid or expired token"));

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                authenticationService.getUsernameByToken(invalidToken)
        );

        assertEquals("Invalid or expired token", exception.getMessage());
    }

    @Test
    void loginWithSameUsernameAndPasswordMessageForUnknownUserAndWrongPassword() {
        // This test ensures the error message is identical to prevent username enumeration
        String username = "attacker_guess";
        String password = "any_password";

        when(customerRepository.findByUsername(username)).thenReturn(Optional.empty());

        UnauthorizedException exceptionForUnknownUser = assertThrows(UnauthorizedException.class, () ->
                authenticationService.login(username, password)
        );

        // Now test with a known user but wrong password
        Customer customer = new Customer(
                "Real User",
                "Address",
                "real_user",
                LocalDate.of(1990, 1, 1),
                "NL",
                "correct_password"
        );

        when(customerRepository.findByUsername("real_user")).thenReturn(Optional.of(customer));

        UnauthorizedException exceptionForWrongPassword = assertThrows(UnauthorizedException.class, () ->
                authenticationService.login("real_user", "wrong_password")
        );

        // Both exceptions should have the same message
        assertEquals(exceptionForUnknownUser.getMessage(), exceptionForWrongPassword.getMessage());
    }
}

