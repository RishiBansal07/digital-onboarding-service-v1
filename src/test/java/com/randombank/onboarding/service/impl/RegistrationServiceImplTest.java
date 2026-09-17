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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private IbanGenerator ibanGenerator;

    private RegistrationServiceImpl registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationServiceImpl(
                customerRepository,
                accountRepository,
                ibanGenerator
        );
    }

    @Test
    void registerCustomerSuccessfully() {
        RegisterRequest request = new RegisterRequest(
                "John Doe",
                "Damrak 1, Amsterdam",
                "john_doe",
                LocalDate.of(1990, 5, 20),
                "NL"
        );

        when(customerRepository.existsByUsername("john_doe")).thenReturn(false);
        when(ibanGenerator.generateIban()).thenReturn("NL91RABO0417164300");
        when(accountRepository.existsByIban("NL91RABO0417164300")).thenReturn(false);

        Customer savedCustomer = new Customer(
                "John Doe",
                "Damrak 1, Amsterdam",
                "john_doe",
                LocalDate.of(1990, 5, 20),
                "NL",
                "password123"
        );
        when(customerRepository.saveAndFlush(any(Customer.class))).thenReturn(savedCustomer);

        Account savedAccount = new Account(
                "NL91RABO0417164300",
                AccountType.CURRENT,
                BigDecimal.ZERO,
                "EUR",
                savedCustomer
        );
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        Customer result = registrationService.registerCustomer(request);

        assertNotNull(result);
        assertEquals("john_doe", result.getUsername());
        verify(customerRepository).saveAndFlush(any(Customer.class));
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void registerCustomerThrowsConflictExceptionWhenUsernameExists() {
        RegisterRequest request = new RegisterRequest(
                "Jane Doe",
                "Damrak 1, Amsterdam",
                "existing_user",
                LocalDate.of(1990, 5, 20),
                "NL"
        );

        when(customerRepository.existsByUsername("existing_user")).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class, () ->
                registrationService.registerCustomer(request)
        );

        assertEquals("Username already exists", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void registerCustomerTranslatesConcurrentUsernameConstraintViolationToConflict() {
        RegisterRequest request = new RegisterRequest(
                "Jane Doe",
                "Damrak 1, Amsterdam",
                "racing_user",
                LocalDate.of(1990, 5, 20),
                "NL"
        );
        when(customerRepository.existsByUsername("racing_user")).thenReturn(false);
        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate username",
                        new ConstraintViolationException("duplicate username", new SQLException(),
                                "uk_customers_username")));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> registrationService.registerCustomer(request));

        assertEquals("Username already exists", exception.getMessage());
        verifyNoInteractions(accountRepository, ibanGenerator);
    }

    @Test
    void registerCustomerDoesNotMislabelOtherConstraintViolations() {
        RegisterRequest request = new RegisterRequest(
                "Jane Doe",
                "Damrak 1, Amsterdam",
                "other_constraint_user",
                LocalDate.of(1990, 5, 20),
                "NL"
        );
        DataIntegrityViolationException databaseFailure = new DataIntegrityViolationException(
                "constraint violation",
                new ConstraintViolationException("constraint violation", new SQLException(), "uk_other"));
        when(customerRepository.existsByUsername("other_constraint_user")).thenReturn(false);
        when(customerRepository.saveAndFlush(any(Customer.class))).thenThrow(databaseFailure);

        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> registrationService.registerCustomer(request));

        assertSame(databaseFailure, exception);
    }

    @Test
    void registerCustomerThrowsBadRequestExceptionWhenIbanCollisionAfterMaxAttempts() {
        RegisterRequest request = new RegisterRequest(
                "Bob Smith",
                "Damrak 1, Amsterdam",
                "bob_smith",
                LocalDate.of(1990, 5, 20),
                "NL"
        );

        when(customerRepository.existsByUsername("bob_smith")).thenReturn(false);
        when(ibanGenerator.generateIban()).thenReturn("NL91RABO0417164300");
        when(accountRepository.existsByIban("NL91RABO0417164300")).thenReturn(true); // Always collision

        Customer savedCustomer = new Customer(
                "Bob Smith",
                "Damrak 1, Amsterdam",
                "bob_smith",
                LocalDate.of(1990, 5, 20),
                "NL",
                "password"
        );
        when(customerRepository.saveAndFlush(any(Customer.class))).thenReturn(savedCustomer);

        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                registrationService.registerCustomer(request)
        );

        assertTrue(exception.getMessage().contains("Failed to generate a unique IBAN"));
        // Customer save is called even though IBAN generation fails later
        verify(customerRepository).saveAndFlush(any(Customer.class));
    }

    @Test
    void registerCustomerGeneratesUniqueIbanAfterRetry() {
        RegisterRequest request = new RegisterRequest(
                "Alice Johnson",
                "Damrak 1, Amsterdam",
                "alice_johnson",
                LocalDate.of(1990, 5, 20),
                "NL"
        );

        when(customerRepository.existsByUsername("alice_johnson")).thenReturn(false);
        // First call returns collision, second call returns unique IBAN
        when(ibanGenerator.generateIban())
                .thenReturn("NL91RABO0417164300")
                .thenReturn("NL92RABO0417164301");
        when(accountRepository.existsByIban("NL91RABO0417164300")).thenReturn(true);
        when(accountRepository.existsByIban("NL92RABO0417164301")).thenReturn(false);

        Customer savedCustomer = new Customer(
                "Alice Johnson",
                "Damrak 1, Amsterdam",
                "alice_johnson",
                LocalDate.of(1990, 5, 20),
                "NL",
                "password456"
        );
        when(customerRepository.saveAndFlush(any(Customer.class))).thenReturn(savedCustomer);

        Account savedAccount = new Account(
                "NL92RABO0417164301",
                AccountType.CURRENT,
                BigDecimal.ZERO,
                "EUR",
                savedCustomer
        );
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        Customer result = registrationService.registerCustomer(request);

        assertNotNull(result);
        assertEquals("alice_johnson", result.getUsername());
        verify(ibanGenerator, times(2)).generateIban();
        verify(accountRepository, times(2)).existsByIban(anyString());
    }

    @Test
    void registerCustomerCreatesAccountWithDefaultCurrencyEUR() {
        RegisterRequest request = new RegisterRequest(
                "Test User",
                "Test Address",
                "test_user",
                LocalDate.of(1990, 1, 1),
                "NL"
        );

        when(customerRepository.existsByUsername("test_user")).thenReturn(false);
        when(ibanGenerator.generateIban()).thenReturn("NL91RABO0417164300");
        when(accountRepository.existsByIban("NL91RABO0417164300")).thenReturn(false);

        Customer savedCustomer = new Customer(
                "Test User",
                "Test Address",
                "test_user",
                LocalDate.of(1990, 1, 1),
                "NL",
                "password789"
        );
        when(customerRepository.saveAndFlush(any(Customer.class))).thenReturn(savedCustomer);

        Account savedAccount = new Account(
                "NL91RABO0417164300",
                AccountType.CURRENT,
                BigDecimal.ZERO,
                "EUR",
                savedCustomer
        );
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        registrationService.registerCustomer(request);

        verify(accountRepository).save(argThat(account ->
                account.getCurrency().equals("EUR") &&
                account.getBalance().compareTo(BigDecimal.ZERO) == 0 &&
                account.getAccountType() == AccountType.CURRENT
        ));
    }
}
