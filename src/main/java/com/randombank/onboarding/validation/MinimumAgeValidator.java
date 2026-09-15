package com.randombank.onboarding.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class MinimumAgeValidator implements ConstraintValidator<MinimumAge, LocalDate> {

    private int minimumAge;

    @Override
    public void initialize(MinimumAge constraintAnnotation) {
        this.minimumAge = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        LocalDate today = LocalDate.now();
        LocalDate minimumBirthDate = today.minusYears(minimumAge);

        return !value.isAfter(minimumBirthDate);
    }
}

