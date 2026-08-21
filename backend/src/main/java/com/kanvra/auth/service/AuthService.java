package com.kanvra.auth.service;

import com.kanvra.auth.dto.LoginRequest;
import com.kanvra.auth.dto.RegisterRequest;
import com.kanvra.auth.model.RefreshToken;
import com.kanvra.auth.model.User;
import com.kanvra.auth.repository.RefreshTokenRepository;
import com.kanvra.auth.repository.UserRepository;
import com.kanvra.common.config.KanvraProperties;
import com.kanvra.common.error.DuplicateEmailException;
import com.kanvra.common.error.ResourceNotFoundException;
import com.kanvra.common.error.UnauthorizedException;
import com.kanvra.common.security.AuthenticatedUser;
import com.kanvra.common.security.JwtService;
import com.kanvra.common.security.JwtTokenType;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, refresh, and current-user lookup (SPEC.md §3).
 *
 * <p>Refresh tokens are persisted server-side ({@code refresh_tokens}) so that
 * rotation actually invalidates the old token and reuse of a rotated-out token
 * can be detected and answered by revoking the whole family (review finding:
 * previously a stolen refresh token stayed valid for its full 7-day TTL).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final KanvraProperties properties;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenRepository refreshTokenRepository, KanvraProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
    }

    @Transactional
    public AuthSession register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("An account with this email already exists");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return issueSession(user);
    }

    public AuthSession login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .map(found -> {
                    if (!passwordEncoder.matches(request.password(), found.getPasswordHash())) {
                        throw new UnauthorizedException("Invalid email or password");
                    }
                    return found;
                })
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        return issueSession(user);
    }

    /**
     * Rotates the refresh token: the presented token is revoked and a fresh pair
     * is issued. Presenting an already-revoked token is treated as reuse of a
     * stolen/leaked token and revokes the user's entire refresh-token family.
     */
    @Transactional
    public AuthSession refresh(String rawRefreshToken) {
        String jti;
        try {
            jti = jwtService.extractJti(rawRefreshToken, JwtTokenType.REFRESH);
            jwtService.parse(rawRefreshToken, JwtTokenType.REFRESH); // validates signature/expiry
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        RefreshToken stored = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (stored.getRevokedAt() != null) {
            // A revoked token is being presented — possible theft. Kill the family.
            refreshTokenRepository.revokeAllActiveForUser(stored.getUserId(), Instant.now());
            throw new UnauthorizedException("Refresh token has been revoked; please sign in again");
        }
        if (!stored.getExpiresAt().isAfter(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Refresh token does not reference a valid user"));
        return rotate(user, stored);
    }


    /** Revokes every active refresh token for a user (logout). */
    @Transactional
    public void revokeAllRefreshTokens(Long userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
    }

    public User me(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));
    }

    private AuthSession issueSession(User user) {
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        String jti = UUID.randomUUID().toString();
        String accessToken = jwtService.createAccessToken(principal);
        String refreshToken = jwtService.createRefreshToken(principal, jti);
        persistRefreshToken(user.getId(), jti);
        return new AuthSession(user, accessToken, refreshToken, jti);
    }

    private AuthSession rotate(User user, RefreshToken oldRow) {
        String newJti = UUID.randomUUID().toString();
        String accessToken = jwtService.createAccessToken(AuthenticatedUser.from(user));
        String refreshToken = jwtService.createRefreshToken(AuthenticatedUser.from(user), newJti);
        RefreshToken newRow = persistRefreshToken(user.getId(), newJti);

        oldRow.setRevokedAt(Instant.now());
        oldRow.setReplacedBy(newRow.getId());
        refreshTokenRepository.save(oldRow);

        return new AuthSession(user, accessToken, refreshToken, newJti);
    }

    private RefreshToken persistRefreshToken(Long userId, String jti) {
        RefreshToken row = new RefreshToken();
        row.setUserId(userId);
        row.setJti(jti);
        row.setExpiresAt(Instant.now().plus(properties.getJwt().getRefreshTokenTtl()));
        return refreshTokenRepository.saveAndFlush(row);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
