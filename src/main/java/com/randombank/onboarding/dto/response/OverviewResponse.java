package com.randombank.onboarding.dto.response;

import java.math.BigDecimal;

public record OverviewResponse(
        String accountNumber,
        String accountType,
        BigDecimal balance,
        String currency
) {
}

