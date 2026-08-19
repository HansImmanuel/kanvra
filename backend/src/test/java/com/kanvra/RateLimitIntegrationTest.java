package com.kanvra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the real 429 HTTP path: once the per-IP auth bucket is exhausted the
 * API returns {@code 429 TOO_MANY_REQUESTS} (SPEC.md §16). Uses a tiny limit via
 * {@link TestPropertySource} so the whole class runs against a shared client IP.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "kanvra.auth.rate-limit-per-minute=3")
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginExhaustsBucketThenReturns429() throws Exception {
        // Seed a known user first; the limit applies to /login and /register alike.
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Iris","email":"iris@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated());

        String body = """
                {"email":"iris@example.com","password":"password123"}
                """;

        // Register consumed 1 of the 3 permits; two logins remain, then 429.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }
}
