package com.randombank.onboarding.dto.response;

public record RegisterResponse(
        String username,
        String defaultPassword,
        String message
) {
}

