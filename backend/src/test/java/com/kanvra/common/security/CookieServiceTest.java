package com.kanvra.common.security;

import com.kanvra.common.config.KanvraProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Secure-flag resolution for auth cookies (Sprint 4): {@code Secure} is set when
 * {@code kanvra.cookies.secure} OR {@code server.ssl.enabled} is true — never
 * derived from the request scheme (breaks behind reverse proxies).
 */
class CookieServiceTest {

    private final KanvraProperties properties = new KanvraProperties();
    private final MockEnvironment environment = new MockEnvironment();

    private String accessSetCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CookieService(properties, environment)
                .addSessionCookies(response, "access", "refresh", Duration.ofMinutes(5), Duration.ofDays(1));
        return response.getHeader(HttpHeaders.SET_COOKIE);
    }

    @Test
    void insecureByDefaultInLocalDev() {
        assertThat(accessSetCookie()).doesNotContain("Secure");
    }

    @Test
    void explicitFlagForcesSecure() {
        properties.getCookies().setSecure(true);
        assertThat(accessSetCookie()).contains("Secure");
    }

    @Test
    void sslEnabledForcesSecureWithoutTheFlag() {
        environment.setProperty("server.ssl.enabled", "true");
        assertThat(accessSetCookie()).contains("Secure");
    }
}