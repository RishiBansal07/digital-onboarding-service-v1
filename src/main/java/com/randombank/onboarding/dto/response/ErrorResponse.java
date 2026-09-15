package com.randombank.onboarding.dto.response;

public record ErrorResponse(
        String code,
        String message,
        String details,
        long timestamp
) {
}

