package com.kanvra.common.security;

import com.kanvra.common.config.KanvraProperties;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        KanvraProperties properties = new KanvraProperties();
        properties.getJwt().setSecret("a-valid-secret-that-is-long-enough-for-hs256-signing");
        properties.getJwt().setAccessTokenTtl(Duration.ofMinutes(30));
        properties.getJwt().setRefreshTokenTtl(Duration.ofDays(7));
        jwtService = new JwtService(properties);
    }

    @Test
    void accessTokenRoundTripsUser() {
        AuthenticatedUser user = new AuthenticatedUser(42L, "Hans", "hans@example.com");

        String token = jwtService.createAccessToken(user);

        AuthenticatedUser parsed = jwtService.parse(token, JwtTokenType.ACCESS);
        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.name()).isEqualTo("Hans");
        assertThat(parsed.email()).isEqualTo("hans@example.com");
    }

    @Test
    void refreshTokenCarriesJtiAndRoundTripsUser() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "A", "a@example.com");

        String refresh = jwtService.createRefreshToken(user, "refresh-jti-123");

        AuthenticatedUser parsed = jwtService.parse(refresh, JwtTokenType.REFRESH);
        assertThat(parsed.id()).isEqualTo(1L);
        assertThat(jwtService.extractJti(refresh, JwtTokenType.REFRESH)).isEqualTo("refresh-jti-123");
    }

    @Test
    void accessTokenHasNoJti() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "A", "a@example.com");
        String access = jwtService.createAccessToken(user);

        assertThatThrownBy(() -> jwtService.extractJti(access, JwtTokenType.ACCESS))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "A", "a@example.com");
        String refresh = jwtService.createRefreshToken(user, "jti-1");

        assertThatThrownBy(() -> jwtService.parse(refresh, JwtTokenType.ACCESS))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "A", "a@example.com");
        String access = jwtService.createAccessToken(user);

        assertThatThrownBy(() -> jwtService.parse(access, JwtTokenType.REFRESH))
                .isInstanceOf(JwtException.class);
    }
}
