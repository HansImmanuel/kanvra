package com.kanvra.auth.controller;

import com.kanvra.auth.dto.LoginRequest;
import com.kanvra.auth.dto.RegisterRequest;
import com.kanvra.auth.dto.UserResponse;
import com.kanvra.auth.model.User;
import com.kanvra.auth.ratelimit.AuthRateLimiter;
import com.kanvra.auth.service.AuthService;
import com.kanvra.common.config.KanvraProperties;
import com.kanvra.common.error.UnauthorizedException;
import com.kanvra.common.security.AuthenticatedUser;
import com.kanvra.common.security.CookieService;
import com.kanvra.common.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final KanvraProperties properties;

    public AuthController(AuthService authService, JwtService jwtService, CookieService cookieService,
                          AuthRateLimiter rateLimiter, KanvraProperties properties) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieService = cookieService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimiter.check(clientIp(httpRequest));
        User user = authService.register(request);
        issueSession(httpResponse, user);
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimiter.check(clientIp(httpRequest));
        User user = authService.login(request);
        issueSession(httpResponse, user);
        return UserResponse.from(user);
    }

    @PostMapping("/refresh")
    public UserResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = cookieService.readRefreshToken(httpRequest);
        if (refreshToken == null) {
            throw new UnauthorizedException("Missing refresh token");
        }
        User user = authService.refresh(refreshToken);
        issueSession(httpResponse, user);
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse httpResponse) {
        cookieService.clearCookies(httpResponse);
        return ResponseEntity.noContent().build();
    }

    private void issueSession(HttpServletResponse response, User user) {
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        String access = jwtService.createAccessToken(principal);
        String refresh = jwtService.createRefreshToken(principal);
        cookieService.addSessionCookies(response, access, refresh,
                properties.getJwt().getAccessTokenTtl(), properties.getJwt().getRefreshTokenTtl());
        cookieService.addCsrfCookie(response, cookieService.generateCsrfValue());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
