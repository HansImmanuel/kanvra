package com.kanvra;

import com.kanvra.common.config.KanvraProperties;
import com.kanvra.common.security.AuthenticatedUser;
import com.kanvra.common.security.JwtService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
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
                                {"name":"","email":"not-an-email","password":"Pass1"}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                // name (blank) + email (invalid) + password (too short) — "Pass1"
                // satisfies the letter+digit pattern so it fails @Size only.
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

        // The rotated-out token must no longer work (server-side revocation).
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshReuseAfterLogoutIsRejected() throws Exception {
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Eve","email":"eve@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie access = register.getResponse().getCookie("access_token");
        Cookie refresh = register.getResponse().getCookie("refresh_token");
        Cookie csrf = register.getResponse().getCookie("csrf_token");

        // Logout revokes the user's refresh tokens server-side.
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(access, refresh, csrf)
                        .header("X-CSRF-Token", csrf.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refresh.getValue())))
                .andExpect(status().isUnauthorized());
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
                        .cookie(access, refresh, csrf)
                        .header("X-CSRF-Token", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(cookie().maxAge("csrf_token", 0));
    }

    @Test
    void logoutWithoutCsrfTokenIsRejected() throws Exception {
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Frank","email":"frank@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie access = register.getResponse().getCookie("access_token");
        Cookie refresh = register.getResponse().getCookie("refresh_token");
        Cookie csrf = register.getResponse().getCookie("csrf_token");

        // Logout is NOT exempt from CSRF: a cookie-bearing request without a
        // matching X-CSRF-Token header is rejected (logout-CSRF hardening).
        mockMvc.perform(post("/api/v1/auth/logout").cookie(access, refresh, csrf))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void bearerAuthenticatedMutationSkipsCsrf() throws Exception {
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grace","email":"grace@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String accessToken = register.getResponse().getCookie("access_token").getValue();

        // A Bearer-authenticated client has no session cookies, so CSRF does not
        // apply (decision b from the Sprint 2 review); the request must succeed.
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Helen","email":"helen@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        int userId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(register.getResponse().getContentAsString()).get("id").asInt();

        // Mint an already-expired access token with the app's own signing key.
        KanvraProperties expiredProps = new KanvraProperties();
        expiredProps.getJwt().setAccessTokenTtl(Duration.ofSeconds(-1));
        String expired = new JwtService(expiredProps)
                .createAccessToken(new AuthenticatedUser((long) userId, "Helen", "helen@example.com"));

        mockMvc.perform(get("/api/v1/me").cookie(new Cookie("access_token", expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}