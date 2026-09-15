package com.randombank.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the legacy-database protection: once the per-second budget is spent,
 * further requests are rejected with 429 before reaching the controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.db.max-requests-per-second=1")
class ApiRateLimitFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void secondRequestWithinTheSameSecondIsThrottled() throws Exception {
        // First request consumes the budget. It fails auth (401), which is fine —
        // what matters is that it reached the controller.
        mockMvc.perform(get("/overview").header("Authorization", "Bearer some-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/overview").header("Authorization", "Bearer some-token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }
}

