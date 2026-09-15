package com.randombank.onboarding.service.impl;

import com.randombank.onboarding.domain.entity.Account;
import com.randombank.onboarding.dto.response.OverviewResponse;
import com.randombank.onboarding.exception.NotFoundException;
import com.randombank.onboarding.repository.AccountRepository;
import com.randombank.onboarding.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    public OverviewResponse getAccountOverview(String username) {
        Account account = accountRepository.findByCustomerUsername(username)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        return new OverviewResponse(
                account.getIban(),
                account.getAccountType().toString(),
                account.getBalance(),
                account.getCurrency()
        );
    }
}

