package com.randombank.onboarding.repository;

import com.randombank.onboarding.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByUsername(String username);

    Optional<Customer> findByUsername(String username);
}

