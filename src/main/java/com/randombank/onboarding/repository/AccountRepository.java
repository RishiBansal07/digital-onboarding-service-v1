package com.randombank.onboarding.repository;

import com.randombank.onboarding.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByIban(String iban);

    Optional<Account> findByCustomerUsername(String username);
}

