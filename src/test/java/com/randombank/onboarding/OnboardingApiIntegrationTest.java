package com.randombank.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the main onboarding flow and the most important failure cases.
 * The rate limit is raised here so it does not interfere; it has its own test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.db.max-requests-per-second=1000")
class OnboardingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerThenLoginThenViewOverview() throws Exception {
        String username = "happy_path_user";

        String registerBody = mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(username, "1990-05-20", "NL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.defaultPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String password = json(registerBody).get("defaultPassword").asText();

        String loginBody = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = json(loginBody).get("token").asText();

        mockMvc.perform(get("/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(org.hamcrest.Matchers.startsWith("NL")))
                .andExpect(jsonPath("$.accountType").value("CURRENT"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String username = "duplicate_user";

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(username, "1990-05-20", "NL")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(username, "1990-05-20", "NL")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void customerUnderEighteenIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("too_young", "2015-01-01", "NL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void disallowedCountryIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("wrong_country", "1990-05-20", "DE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        String username = "wrong_password_user";

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(username, "1990-05-20", "NL")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "not-the-password" }
                                """.formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void overviewWithInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/overview").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void overviewWithoutAuthorizationHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(get("/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void overviewWithNonBearerAuthorizationHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(get("/overview").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void overviewWithEmptyBearerTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/overview").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private String registrationJson(String username, String dateOfBirth, String countryCode) {
        return """
                {
                  "fullName": "Test Customer",
                  "address": "Damrak 1, Amsterdam",
                  "username": "%s",
                  "dateOfBirth": "%s",
                  "countryCode": "%s"
                }
                """.formatted(username, dateOfBirth, countryCode);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}

