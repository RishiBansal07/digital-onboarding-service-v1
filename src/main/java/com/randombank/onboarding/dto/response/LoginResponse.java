package com.randombank.onboarding.dto.response;

public record LoginResponse(
        String username,
        String token,
        String message
) {
}

