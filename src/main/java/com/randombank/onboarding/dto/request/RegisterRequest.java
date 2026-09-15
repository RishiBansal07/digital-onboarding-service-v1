package com.randombank.onboarding.dto.request;

import com.randombank.onboarding.validation.AllowedCountry;
import com.randombank.onboarding.validation.MinimumAge;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Address is required")
        @Size(min = 5, max = 200, message = "Address must be between 5 and 200 characters")
        String address,

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain alphanumeric characters, dots, underscores, and hyphens")
        String username,

        @NotNull(message = "Date of birth is required")
        @MinimumAge(value = 18, message = "Customer must be at least 18 years old")
        LocalDate dateOfBirth,

        @NotBlank(message = "Country code is required")
        @Size(min = 2, max = 2, message = "Country code must be exactly 2 characters")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country code must be uppercase ISO 3166-1 alpha-2 format")
        @AllowedCountry(message = "Country is not allowed for registration")
        String countryCode
) {
}


