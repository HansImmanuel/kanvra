package com.kanvra.auth.controller;

import com.kanvra.auth.dto.LoginRequest;
import com.kanvra.auth.dto.RegisterRequest;
import com.kanvra.auth.dto.UserResponse;
import com.kanvra.auth.ratelimit.AuthRateLimiter;
import com.kanvra.auth.ratelimit.ClientIpResolver;
import com.kanvra.auth.service.AuthService;
import com.kanvra.auth.service.AuthSession;
import com.kanvra.common.config.KanvraProperties;
import com.kanvra.common.error.UnauthorizedException;
import com.kanvra.common.security.AuthenticatedUser;
import com.kanvra.common.security.CookieService;
import com.kanvra.common.security.JwtService;
import com.kanvra.common.security.JwtTokenType;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints (SPEC.md §3): register, login, refresh, logout. Tokens are
 * delivered via httpOnly cookies; the response body carries only the user.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final CookieService cookieService;
    private final AuthRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final KanvraProperties properties;

    public AuthController(AuthService authService, JwtService jwtService, CookieService cookieService,
                          AuthRateLimiter rateLimiter, ClientIpResolver clientIpResolver,
                          KanvraProperties properties) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieService = cookieService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.properties = properties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimiter.check(clientIpResolver.resolve(httpRequest));
        AuthSession session = authService.register(request);
        issueSession(httpResponse, session);
        return UserResponse.from(session.user());
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimiter.check(clientIpResolver.resolve(httpRequest));
        AuthSession session = authService.login(request);
        issueSession(httpResponse, session);
        return UserResponse.from(session.user());
    }

    @PostMapping("/refresh")
    public UserResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = cookieService.readRefreshToken(httpRequest);
        if (refreshToken == null) {
            throw new UnauthorizedException("Missing refresh token");
        }
        AuthSession session = authService.refresh(refreshToken);
        issueSession(httpResponse, session);
        return UserResponse.from(session.user());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Long userId = currentUserId(httpRequest);
        if (userId != null) {
            authService.revokeAllRefreshTokens(userId);
        }
        cookieService.clearCookies(httpResponse);
        return ResponseEntity.noContent().build();
    }

    private void issueSession(HttpServletResponse response, AuthSession session) {
        cookieService.addSessionCookies(response, session.accessToken(), session.refreshToken(),
                properties.getJwt().getAccessTokenTtl(), properties.getJwt().getRefreshTokenTtl());
        cookieService.addCsrfCookie(response, cookieService.generateCsrfValue());
    }

    /**
     * Best-effort identity for logout: prefers the already-authenticated
     * principal, otherwise parses the access cookie directly so logout still
     * revokes tokens when the access token itself has expired.
     */
    private Long currentUserId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        String accessToken = cookieService.readAccessToken(request);
        if (accessToken != null) {
            try {
                return jwtService.parse(accessToken, JwtTokenType.ACCESS).id();
            } catch (JwtException | IllegalArgumentException ignored) {
                // Expired/invalid access token: no identity available.
            }
        }
        return null;
    }
}
