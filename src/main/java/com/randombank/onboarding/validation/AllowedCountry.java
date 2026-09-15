package com.randombank.onboarding.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AllowedCountryValidator.class)
@Documented
public @interface AllowedCountry {

    String message() default "Country is not allowed for registration";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

