package com.randombank.onboarding.service.impl;

import com.randombank.onboarding.domain.entity.Account;
import com.randombank.onboarding.domain.entity.Customer;
import com.randombank.onboarding.domain.enums.AccountType;
import com.randombank.onboarding.dto.response.OverviewResponse;
import com.randombank.onboarding.exception.NotFoundException;
import com.randombank.onboarding.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(accountRepository);
    }

    @Test
    void getAccountOverviewReturnsCorrectResponse() {
        String username = "john_doe";
        Customer customer = new Customer(
                "John Doe",
                "Damrak 1, Amsterdam",
                username,
                LocalDate.of(1990, 5, 20),
                "NL",
                "password123"
        );

        Account account = new Account(
                "NL91RABO0417164300",
                AccountType.CURRENT,
                new BigDecimal("1000.50"),
                "EUR",
                customer
        );

        when(accountRepository.findByCustomerUsername(username)).thenReturn(Optional.of(account));

        OverviewResponse response = accountService.getAccountOverview(username);

        assertNotNull(response);
        assertEquals("NL91RABO0417164300", response.accountNumber());
        assertEquals("CURRENT", response.accountType());
        assertEquals(new BigDecimal("1000.50"), response.balance());
        assertEquals("EUR", response.currency());
        verify(accountRepository).findByCustomerUsername(username);
    }

    @Test
    void getAccountOverviewWithZeroBalanceReturnsZero() {
        String username = "new_customer";
        Customer customer = new Customer(
                "New Customer",
                "Amsterdam",
                username,
                LocalDate.of(2005, 1, 1),
                "NL",
                "password456"
        );

        Account account = new Account(
                "NL92RABO0417164301",
                AccountType.CURRENT,
                BigDecimal.ZERO,
                "EUR",
                customer
        );

        when(accountRepository.findByCustomerUsername(username)).thenReturn(Optional.of(account));

        OverviewResponse response = accountService.getAccountOverview(username);

        assertEquals(BigDecimal.ZERO, response.balance());
    }

    @Test
    void getAccountOverviewThrowsNotFoundExceptionWhenAccountDoesNotExist() {
        String username = "non_existent_user";

        when(accountRepository.findByCustomerUsername(username)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () ->
                accountService.getAccountOverview(username)
        );

        assertEquals("Account not found", exception.getMessage());
        verify(accountRepository).findByCustomerUsername(username);
    }

    @Test
    void getAccountOverviewReturnsDifferentAccountTypes() {
        String username = "business_customer";
        Customer customer = new Customer(
                "Business Corp",
                "Business Street",
                username,
                LocalDate.of(1980, 1, 1),
                "NL",
                "password789"
        );

        Account account = new Account(
                "NL93RABO0417164302",
                AccountType.CURRENT,
                new BigDecimal("50000.00"),
                "EUR",
                customer
        );

        when(accountRepository.findByCustomerUsername(username)).thenReturn(Optional.of(account));

        OverviewResponse response = accountService.getAccountOverview(username);

        assertEquals(AccountType.CURRENT.toString(), response.accountType());
    }

    @Test
    void getAccountOverviewReturnsCorrectCurrency() {
        String username = "euro_customer";
        Customer customer = new Customer(
                "Euro User",
                "Amsterdam",
                username,
                LocalDate.of(1995, 1, 1),
                "NL",
                "password000"
        );

        Account account = new Account(
                "NL94RABO0417164303",
                AccountType.CURRENT,
                new BigDecimal("2500.75"),
                "EUR",
                customer
        );

        when(accountRepository.findByCustomerUsername(username)).thenReturn(Optional.of(account));

        OverviewResponse response = accountService.getAccountOverview(username);

        assertEquals("EUR", response.currency());
    }

    @Test
    void getAccountOverviewWithLargeBalance() {
        String username = "wealthy_customer";
        Customer customer = new Customer(
                "Rich Person",
                "Amsterdam",
                username,
                LocalDate.of(1970, 1, 1),
                "NL",
                "password111"
        );

        Account account = new Account(
                "NL95RABO0417164304",
                AccountType.CURRENT,
                new BigDecimal("999999999.99"),
                "EUR",
                customer
        );

        when(accountRepository.findByCustomerUsername(username)).thenReturn(Optional.of(account));

        OverviewResponse response = accountService.getAccountOverview(username);

        assertEquals(new BigDecimal("999999999.99"), response.balance());
    }

    @Test
    void getAccountOverviewReturnsSameIbanAsInDatabase() {
        String username = "iban_test_user";
        String ibanValue = "NL96RABO0417164305";
        Customer customer = new Customer(
                "IBAN Test",
                "Amsterdam",
                username,
                LocalDate.of(2000, 1, 1),
                "NL",
                "password222"
        );

        Account account = new Account(
                ibanValue,
                AccountType.CURRENT,
                new BigDecimal("500.00"),
                "EUR",
                customer
        );

        when(accountRepository.findByCustomerUsername(username)).thenReturn(Optional.of(account));

        OverviewResponse response = accountService.getAccountOverview(username);

        assertEquals(ibanValue, response.accountNumber());
    }
}

