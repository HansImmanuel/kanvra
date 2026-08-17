package com.kanvra;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth flow against PostgreSQL (Flyway-migrated) and the real
 * security filter chain: register -> /me -> refresh -> logout, plus error
 * behaviors (SPEC.md §3, §17).
 */
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerIssuesCookiesAndMeReturnsUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hans","email":"hans@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().exists("csrf_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().httpOnly("csrf_token", false))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("hans@example.com"))
                .andReturn();

        String accessToken = result.getResponse().getCookie("access_token").getValue();

        mockMvc.perform(get("/api/v1/me").cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("hans@example.com"));
    }

    @Test
    void duplicateEmailReturns409DuplicateEmail() throws Exception {
        String body = """
                {"name":"Alice","email":"dup@example.com","password":"password123"}
                """;
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bob","email":"DUP@example.com","password":"password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void invalidRegistrationReturns422WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void loginThenRefreshRotatesSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Carol","email":"carol@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carol@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carol@example.com"))
                .andReturn();

        String refreshToken = login.getResponse().getCookie("refresh_token").getValue();

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void logoutClearsCookies() throws Exception {
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dan","email":"dan@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie access = register.getResponse().getCookie("access_token");
        Cookie refresh = register.getResponse().getCookie("refresh_token");
        Cookie csrf = register.getResponse().getCookie("csrf_token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(access, refresh, csrf))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(cookie().maxAge("csrf_token", 0));
    }
}