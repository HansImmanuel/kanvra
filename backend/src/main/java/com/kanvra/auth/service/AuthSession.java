package com.kanvra.auth.service;

import com.kanvra.auth.model.User;

/**
 * Internal result of a successful session issue (login/register/refresh):
 * the user plus the freshly-minted access and refresh tokens. The refresh
 * token's {@code jti} is returned so the caller can persist it to the
 * {@code refresh_tokens} table (rotation/revocation support).
 */
public record AuthSession(User user, String accessToken, String refreshToken, String refreshTokenJti) {
}
