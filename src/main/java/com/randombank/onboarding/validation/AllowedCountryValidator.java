package com.randombank.onboarding.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class AllowedCountryValidator implements ConstraintValidator<AllowedCountry, String> {

    @Value("${app.registration.allowed-countries}")
    private List<String> allowedCountries;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null handling is the responsibility of @NotBlank, not this validator.
        if (value == null) {
            return true;
        }

        String countryCode = value.trim().toUpperCase(Locale.ROOT);

        return allowedCountries.stream()
                .anyMatch(allowed -> allowed.trim().toUpperCase(Locale.ROOT).equals(countryCode));
    }
}
