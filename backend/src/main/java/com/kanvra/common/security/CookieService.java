package com.kanvra.common.security;

import com.kanvra.common.config.KanvraProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Adds/clears the auth cookies described in SPEC.md §3.1:
 * <ul>
 *   <li>{@code access_token} - httpOnly, SameSite=Lax, short-lived</li>
 *   <li>{@code refresh_token} - httpOnly, SameSite=Strict, path-scoped to the refresh endpoint</li>
 *   <li>{@code csrf_token} - NOT httpOnly (readable by JS) for the double-submit pattern</li>
 * </ul>
 */
@Service
public class CookieService {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";
    public static final String CSRF_COOKIE = "csrf_token";

    private final boolean secure;

    public CookieService(KanvraProperties properties) {
        this.secure = properties.getCookies().isSecure();
    }

    public void addSessionCookies(HttpServletResponse response, String accessToken, String refreshToken,
                                  Duration accessTtl, Duration refreshTtl) {
        ResponseCookie access = ResponseCookie.from(ACCESS_COOKIE, accessToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(accessTtl)
                .build();
        ResponseCookie refresh = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api/v1/auth/refresh")
                .maxAge(refreshTtl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, access.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
    }

    public void addCsrfCookie(HttpServletResponse response, String csrfValue) {
        ResponseCookie csrf = ResponseCookie.from(CSRF_COOKIE, csrfValue)
                .httpOnly(false)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, csrf.toString());
    }

    public void clearCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(ACCESS_COOKIE, "").maxAge(0).path("/").build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "").maxAge(0).path("/api/v1/auth/refresh").build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(CSRF_COOKIE, "").maxAge(0).path("/").build().toString());
    }

    public String generateCsrfValue() {
        return UUID.randomUUID().toString();
    }

    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_COOKIE);
    }

    public String readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_COOKIE);
    }

    public static String readCookie(HttpServletRequest request, String name) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
