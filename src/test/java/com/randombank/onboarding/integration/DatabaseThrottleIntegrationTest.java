package com.randombank.onboarding.integration;

import com.randombank.onboarding.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "app.db.max-operations-per-second=20")
class DatabaseThrottleIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void springAopSpacesActualRepositoryCalls() {
        long started = System.nanoTime();

        customerRepository.existsByUsername("rate_limit_probe_1");
        customerRepository.existsByUsername("rate_limit_probe_2");
        customerRepository.existsByUsername("rate_limit_probe_3");

        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        assertTrue(elapsed.compareTo(Duration.ofMillis(80)) >= 0,
                "Three repository calls at 20 operations/second should span about 100 ms, but took " + elapsed);
    }
}

