package com.randombank.onboarding.controller;

import com.randombank.onboarding.domain.entity.Customer;
import com.randombank.onboarding.dto.request.LoginRequest;
import com.randombank.onboarding.dto.request.RegisterRequest;
import com.randombank.onboarding.dto.response.LoginResponse;
import com.randombank.onboarding.dto.response.OverviewResponse;
import com.randombank.onboarding.dto.response.RegisterResponse;
import com.randombank.onboarding.exception.UnauthorizedException;
import com.randombank.onboarding.service.AccountService;
import com.randombank.onboarding.service.AuthenticationService;
import com.randombank.onboarding.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final AccountService accountService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        Customer customer = registrationService.registerCustomer(request);

        RegisterResponse response = new RegisterResponse(
                customer.getUsername(),
                customer.getPassword(),
                "Registration successful"
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authenticationService.login(request.username(), request.password());

        LoginResponse response = new LoginResponse(
                request.username(),
                token,
                "Login successful"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/overview")
    public ResponseEntity<OverviewResponse> overview(@RequestHeader("Authorization") String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        String username = authenticationService.getUsernameByToken(token);
        OverviewResponse response = accountService.getAccountOverview(username);
        return ResponseEntity.ok(response);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Authorization header must use Bearer token");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("Bearer token is missing");
        }

        return token;
    }
}

