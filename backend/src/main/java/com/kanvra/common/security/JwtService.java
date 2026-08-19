package com.kanvra.common.security;

import com.kanvra.common.config.KanvraProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Signs and parses the short-lived access JWT and the long-lived, rotating
 * refresh JWT (SPEC.md §3.1, TECH_DOC.md §16). Access and refresh tokens are
 * signed with the same key but carry a distinct {@code typ} claim so they are
 * not interchangeable.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(KanvraProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.getJwt().getAccessTokenTtl();
        this.refreshTokenTtl = properties.getJwt().getRefreshTokenTtl();
    }

    public String createAccessToken(AuthenticatedUser user) {
        return createToken(user, JwtTokenType.ACCESS, accessTokenTtl, null);
    }

    public String createRefreshToken(AuthenticatedUser user, String jti) {
        return createToken(user, JwtTokenType.REFRESH, refreshTokenTtl, jti);
    }

    private String createToken(AuthenticatedUser user, JwtTokenType type, Duration ttl, String jti) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("name", user.name())
                .claim("email", user.email())
                .claim("typ", type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (jti != null) {
            builder.id(jti);
        }
        return builder.signWith(key).compact();
    }

    /**
     * Parses and validates a token, ensuring it is of the expected type.
     *
     * @throws JwtException if the token is invalid, expired, or of the wrong type
     */
    public AuthenticatedUser parse(String token, JwtTokenType expectedType) {
        Claims claims = parseClaims(token);
        if (!expectedType.name().equals(claims.get("typ", String.class))) {
            throw new JwtException("Token type mismatch");
        }

        return new AuthenticatedUser(
                Long.parseLong(claims.getSubject()),
                claims.get("name", String.class),
                claims.get("email", String.class));
    }

    /**
     * Extracts the {@code jti} claim from a token of the expected type. Used by
     * the refresh flow to look up the persisted {@code refresh_tokens} row.
     *
     * @throws JwtException if the token is invalid, of the wrong type, or has no jti
     */
    public String extractJti(String token, JwtTokenType expectedType) {
        Claims claims = parseClaims(token);
        if (!expectedType.name().equals(claims.get("typ", String.class))) {
            throw new JwtException("Token type mismatch");
        }
        String jti = claims.getId();
        if (jti == null) {
            throw new JwtException("Token has no jti claim");
        }
        return jti;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
