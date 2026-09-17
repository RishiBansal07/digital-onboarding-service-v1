package com.randombank.onboarding.controller;

import com.randombank.onboarding.config.OpenApiConfig;
import com.randombank.onboarding.domain.entity.Customer;
import com.randombank.onboarding.dto.request.LoginRequest;
import com.randombank.onboarding.dto.request.RegisterRequest;
import com.randombank.onboarding.dto.response.LoginResponse;
import com.randombank.onboarding.dto.response.OverviewResponse;
import com.randombank.onboarding.dto.response.RegisterResponse;
import com.randombank.onboarding.service.AccountService;
import com.randombank.onboarding.service.AuthenticationService;
import com.randombank.onboarding.service.RegistrationService;
import com.randombank.onboarding.util.AuthorizationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Onboarding-Controller", description = "Customer onboarding and account management APIs for digital banking")
public class OnboardingController {


    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final AccountService accountService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new customer",
               description = "Register a new customer with personal information. Auto-generates a Dutch IBAN and default password.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Customer registered successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed (invalid age, country not allowed, etc.)"),
            @ApiResponse(responseCode = "409", description = "Username already exists")
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        Customer customer = registrationService.registerCustomer(request);
        log.info("Customer registration completed: country={}", request.countryCode());

        RegisterResponse response = new RegisterResponse(
                customer.getUsername(),
                customer.getPassword(),
                "Registration successful"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Authenticate and obtain session token",
               description = "Log in with username and password. Returns a session token for use in protected endpoints.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid username or password")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authenticationService.login(request.username(), request.password());
        log.info("User login completed");

        LoginResponse response = new LoginResponse(
                request.username(),
                token,
                "Login successful"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/overview")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Retrieve account overview",
               description = "Get the authenticated customer's account details including IBAN, account type, balance, and currency. Requires a valid Bearer token from /login.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account overview retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OverviewResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authorization token"),
            @ApiResponse(responseCode = "404", description = "Account not found for the authenticated user")
    })
    public ResponseEntity<OverviewResponse> overview(
            @Parameter(hidden = true)
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String token = AuthorizationUtil.extractBearerToken(authorizationHeader);
        String username = authenticationService.getUsernameByToken(token);
        OverviewResponse response = accountService.getAccountOverview(username);
        log.info("Account overview retrieved");
        return ResponseEntity.ok(response);
    }
}
