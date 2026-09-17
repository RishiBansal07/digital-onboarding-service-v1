package com.randombank.onboarding.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the main onboarding flow and the most important failure cases.
 * The database-operation rate is raised here so it does not interfere; it has its own test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.db.max-operations-per-second=100000")
class OnboardingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void openApiIncludesEndpointsAndBearerAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.0.")))
                .andExpect(jsonPath("$.paths['/register'].post").exists())
                .andExpect(jsonPath("$.paths['/login'].post").exists())
                .andExpect(jsonPath("$.paths['/overview'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownJsonFieldsRemainIgnored() throws Exception {
        String request = registrationJson("extra_field_user", "1990-05-20", "BE")
                .replace("\"countryCode\": \"BE\"", "\"countryCode\": \"BE\", \"extraField\": true");
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("extra_field_user"));
    }

    @Test
    void customerFromBelgiumCanRegister() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("belgium_customer", "1990-05-20", "BE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("belgium_customer"))
                .andExpect(jsonPath("$.defaultPassword").isNotEmpty());
    }

    @Test
    void customerTurningEighteenTodayCanRegister() throws Exception {
        String eighteenthBirthday = LocalDate.now().minusYears(18).toString();

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("exactly_eighteen", eighteenthBirthday, "NL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("exactly_eighteen"));
    }

    @Test
    void missingRequiredFieldsRemainValidationErrors() throws Exception {
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void registerThenLoginThenViewOverview() throws Exception {
        String username = "happy_path_user";

        String registerBody = mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(username, "1990-05-20", "NL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.defaultPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String password = json(registerBody).get("defaultPassword").asString();

        String loginBody = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = json(loginBody).get("token").asString();

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
                .andExpect(status().isCreated());

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
                .andExpect(status().isCreated());

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "not-the-password" }
                                """.formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void malformedDateOfBirthIsBadRequestNotServerError() throws Exception {
        String body = """
                {
                  "fullName": "Bad Date",
                  "address": "Damrak 1, Amsterdam",
                  "username": "bad_date_user",
                  "dateOfBirth": "20-05-1990",
                  "countryCode": "NL"
                }
                """;

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void malformedJsonIsBadRequestAndDoesNotLeakInternals() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                // The raw Jackson message names internal types; it must not be echoed back.
                .andExpect(jsonPath("$.details").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("java.time"))));
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
