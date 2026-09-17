package com.randombank.onboarding.service.impl;

import com.randombank.onboarding.domain.entity.Account;
import com.randombank.onboarding.domain.entity.Customer;
import com.randombank.onboarding.domain.enums.AccountType;
import com.randombank.onboarding.dto.request.RegisterRequest;
import com.randombank.onboarding.exception.BadRequestException;
import com.randombank.onboarding.exception.ConflictException;
import com.randombank.onboarding.repository.AccountRepository;
import com.randombank.onboarding.repository.CustomerRepository;
import com.randombank.onboarding.service.IbanGenerator;
import com.randombank.onboarding.service.PasswordGenerator;
import com.randombank.onboarding.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
class RegistrationServiceImpl implements RegistrationService {

    private static final String DEFAULT_CURRENCY = "EUR";
    private static final int MAX_IBAN_ATTEMPTS = 10;

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final IbanGenerator ibanGenerator;

    @Override
    @Transactional
    public Customer registerCustomer(RegisterRequest request) {
        if (customerRepository.existsByUsername(request.username())) {
            log.warn("Registration rejected: username already exists");
            throw new ConflictException("Username already exists");
        }

        String defaultPassword = PasswordGenerator.generateDefaultPassword();

        Customer customer = new Customer(
                request.fullName(),
                request.address(),
                request.username(),
                request.dateOfBirth(),
                request.countryCode(),
                defaultPassword
        );

        Customer savedCustomer = customerRepository.save(customer);

        Account account = new Account(
                generateUniqueIban(),
                AccountType.CURRENT,
                BigDecimal.ZERO,
                DEFAULT_CURRENCY,
                savedCustomer
        );

        accountRepository.save(account);
        savedCustomer.setAccount(account);

        return savedCustomer;
    }

    /**
     * Collisions are vanishingly unlikely, but the unique index on accounts.iban
     * means we must not gamble on it.
     */
    private String generateUniqueIban() {
        for (int attempt = 0; attempt < MAX_IBAN_ATTEMPTS; attempt++) {
            String iban = ibanGenerator.generateIban();
            if (!accountRepository.existsByIban(iban)) {
                return iban;
            }
            log.warn("IBAN collision detected, retrying: attempt={}", attempt + 1);
        }

        throw new BadRequestException(
                "Failed to generate a unique IBAN after " + MAX_IBAN_ATTEMPTS + " attempts");
    }
}
