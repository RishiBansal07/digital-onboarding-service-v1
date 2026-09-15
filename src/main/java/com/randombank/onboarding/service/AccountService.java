package com.randombank.onboarding.service;

import com.randombank.onboarding.dto.response.OverviewResponse;

public interface AccountService {
    OverviewResponse getAccountOverview(String username);
}
